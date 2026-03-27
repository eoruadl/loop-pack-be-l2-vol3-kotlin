package com.loopers.application.payment

import com.loopers.application.audit.OrderPaymentAuditEvent
import com.loopers.domain.audit.OrderPaymentAuditEventType
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PaymentService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class PaymentRecoveryFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val pgPaymentPort: PgPaymentPort,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun recoverPayment(paymentId: Long) {
        val payment = paymentService.getPaymentById(paymentId)
        if (payment.status != PaymentStatus.PENDING) return

        val pgStatusResponse = if (payment.pgTxId != null) {
            pgPaymentPort.getPayment(payment.pgTxId!!.value, payment.userId)
        } else {
            pgPaymentPort.getPaymentByOrderId(payment.orderId, payment.userId) ?: run {
                val failedPayment = paymentService.failPayment(payment.id)
                publishRecoveryFailedAudit(failedPayment, "PG 주문 기반 결제 조회 결과가 없습니다.")
                return
            }
        }

        when (pgStatusResponse.status) {
            "SUCCESS" -> {
                val completedPayment = paymentService.completePayment(payment.id)
                val paidOrder = orderService.payOrder(payment.orderId)
                applicationEventPublisher.publishEvent(
                    OrderPaymentAuditEvent(
                        eventType = OrderPaymentAuditEventType.PAYMENT_RECOVERED,
                        orderId = completedPayment.orderId,
                        paymentId = completedPayment.id,
                        userId = completedPayment.userId,
                        orderStatus = paidOrder.status.name,
                        paymentStatus = completedPayment.status.name,
                        cardType = completedPayment.cardType.name,
                        maskedCardNo = completedPayment.cardNo.masked(),
                        pgTransactionId = completedPayment.pgTxId?.value ?: pgStatusResponse.pgTransactionId,
                        reason = "결제 복구 성공",
                    )
                )
            }
            "FAILED" -> when (pgStatusResponse.failureCode) {
                PgFailureCode.LIMIT_EXCEEDED -> publishRecoveryFailedAudit(
                    paymentService.failPayment(payment.id, PaymentStatus.LIMIT_EXCEEDED),
                    PgFailureCode.LIMIT_EXCEEDED.name,
                )
                PgFailureCode.INVALID_CARD -> publishRecoveryFailedAudit(
                    paymentService.failPayment(payment.id, PaymentStatus.INVALID_CARD),
                    PgFailureCode.INVALID_CARD.name,
                )
                else -> publishRecoveryFailedAudit(
                    paymentService.failPayment(payment.id),
                    PgFailureCode.UNKNOWN.name,
                )
            }
            else -> { /* PENDING — 아직 처리 중, 유지 */ }
        }
    }

    fun recoverPendingPayments(olderThanSeconds: Long = 30) {
        val threshold = ZonedDateTime.now().minusSeconds(olderThanSeconds)
        val pendingPayments = paymentService.findPendingPaymentsOlderThan(threshold)
        pendingPayments.forEach { payment ->
            try {
                recoverPayment(payment.id)
            } catch (e: Exception) {
                log.warn("결제 복구 실패 — paymentId={}, reason={}", payment.id, e.message)
            }
        }
    }

    fun getPaymentById(paymentId: Long): PaymentInfo {
        val payment = paymentService.getPaymentById(paymentId)
        return PaymentInfo.from(payment)
    }

    private fun publishRecoveryFailedAudit(
        payment: com.loopers.domain.payment.PaymentModel,
        reason: String,
    ) {
        val order = orderService.getOrderById(payment.orderId)
        applicationEventPublisher.publishEvent(
            OrderPaymentAuditEvent(
                eventType = OrderPaymentAuditEventType.PAYMENT_RECOVERY_FAILED,
                orderId = payment.orderId,
                paymentId = payment.id,
                userId = payment.userId,
                orderStatus = order.status.name,
                paymentStatus = payment.status.name,
                cardType = payment.cardType.name,
                maskedCardNo = payment.cardNo.masked(),
                pgTransactionId = payment.pgTxId?.value,
                reason = reason,
            )
        )
    }
}
