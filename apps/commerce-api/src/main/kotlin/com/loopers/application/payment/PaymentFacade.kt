package com.loopers.application.payment

import com.loopers.application.audit.OrderPaymentAuditEvent
import com.loopers.application.order.OrderEventOutboxCommand
import com.loopers.application.order.OrderEventOutboxService
import com.loopers.domain.audit.OrderPaymentAuditEventType
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.user.UserService
import com.loopers.messaging.order.OrderEventType
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val userService: UserService,
    private val pgPaymentPort: PgPaymentPort,
    @Value("\${payment.callback-url:http://localhost:8080/api/v1/payments/callback}")
    private val callbackUrl: String,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val orderEventOutboxService: OrderEventOutboxService,
) {
    fun requestPayment(loginId: String, orderId: Long, cardType: CardType, cardNo: String): PaymentInfo {
        // 1. 주문 검증
        val user = userService.getUserByLoginId(loginId)
        val order = orderService.getOrderById(orderId)
        if (order.userId != user.id) {
            throw CoreException(ErrorType.FORBIDDEN, "해당 주문에 접근할 권한이 없습니다.")
        }
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 가능한 상태의 주문이 아닙니다.")
        }

        // 2. Payment 레코드 생성 (별도 트랜잭션 — REQUIRES_NEW)
        val payment = paymentService.createPayment(
            orderId = orderId,
            userId = user.id,
            cardType = cardType,
            cardNo = cardNo,
        )

        // 3. PG 호출 (트랜잭션 외부)
        try {
            val pgResponse = pgPaymentPort.requestPayment(
                PgPaymentRequest(
                    orderId = "ORDER-$orderId",
                    userId = user.id,
                    amount = order.totalAmount.value,
                    cardType = cardType.name,
                    cardNo = cardNo,
                    callbackUrl = callbackUrl,
                )
            )
            val updatedPayment = paymentService.setPgTransactionId(payment.id, pgResponse.pgTransactionId)
            applicationEventPublisher.publishEvent(
                OrderPaymentAuditEvent(
                    eventType = OrderPaymentAuditEventType.PAYMENT_REQUESTED,
                    orderId = order.id,
                    paymentId = updatedPayment.id,
                    userId = user.id,
                    orderStatus = order.status.name,
                    paymentStatus = updatedPayment.status.name,
                    cardType = updatedPayment.cardType.name,
                    maskedCardNo = updatedPayment.cardNo.masked(),
                    pgTransactionId = updatedPayment.pgTxId?.value,
                )
            )
        } catch (e: PgPaymentFailException) {
            val failedPayment = paymentService.failPayment(payment.id)
            applicationEventPublisher.publishEvent(
                OrderPaymentAuditEvent(
                    eventType = OrderPaymentAuditEventType.PAYMENT_REQUEST_FAILED,
                    orderId = order.id,
                    paymentId = failedPayment.id,
                    userId = user.id,
                    orderStatus = order.status.name,
                    paymentStatus = failedPayment.status.name,
                    cardType = failedPayment.cardType.name,
                    maskedCardNo = failedPayment.cardNo.masked(),
                    reason = e.message ?: "PG 결제 요청 실패",
                )
            )
            throw CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청에 실패했습니다.")
        } catch (e: PgPaymentTimeoutException) {
            applicationEventPublisher.publishEvent(
                OrderPaymentAuditEvent(
                    eventType = OrderPaymentAuditEventType.PAYMENT_REQUEST_FAILED,
                    orderId = order.id,
                    paymentId = payment.id,
                    userId = user.id,
                    orderStatus = order.status.name,
                    paymentStatus = PaymentStatus.PENDING.name,
                    cardType = cardType.name,
                    maskedCardNo = CardNo(cardNo).masked(),
                    reason = e.message ?: "PG 결제 요청 타임아웃",
                )
            )
            throw CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청이 타임아웃되었습니다.")
        }

        return PaymentInfo.from(paymentService.getPaymentById(payment.id))
    }

    @Transactional
    fun handleCallback(pgTransactionId: String, pgStatus: String, reason: String?) {
        val payment = paymentService.getPaymentByPgTransactionId(pgTransactionId)
        if (payment.status != PaymentStatus.PENDING) return
        when {
            pgStatus == "SUCCESS" -> {
                val completedPayment = paymentService.completePayment(payment.id)
                val paidOrder = orderService.payOrder(payment.orderId)
                applicationEventPublisher.publishEvent(
                    OrderPaymentAuditEvent(
                        eventType = OrderPaymentAuditEventType.PAYMENT_SUCCEEDED,
                        orderId = completedPayment.orderId,
                        paymentId = completedPayment.id,
                        userId = completedPayment.userId,
                        orderStatus = paidOrder.status.name,
                        paymentStatus = completedPayment.status.name,
                        cardType = completedPayment.cardType.name,
                        maskedCardNo = completedPayment.cardNo.masked(),
                        pgTransactionId = completedPayment.pgTxId?.value,
                        reason = reason,
                    )
                )
                orderEventOutboxService.enqueue(
                    OrderEventOutboxCommand(
                        eventType = OrderEventType.PAYMENT_SUCCEEDED,
                        orderId = completedPayment.orderId,
                        paymentId = completedPayment.id,
                        userId = completedPayment.userId,
                    )
                )
            }
            else -> when (parseFailureCode(reason)) {
                PgFailureCode.LIMIT_EXCEEDED -> publishPaymentFailedAudit(
                    paymentService.failPayment(payment.id, PaymentStatus.LIMIT_EXCEEDED),
                    reason,
                )
                PgFailureCode.INVALID_CARD -> publishPaymentFailedAudit(
                    paymentService.failPayment(payment.id, PaymentStatus.INVALID_CARD),
                    reason,
                )
                PgFailureCode.UNKNOWN -> publishPaymentFailedAudit(
                    paymentService.failPayment(payment.id),
                    reason,
                )
            }
        }
    }

    private fun publishPaymentFailedAudit(
        payment: com.loopers.domain.payment.PaymentModel,
        reason: String?,
    ) {
        val order = orderService.getOrderById(payment.orderId)
        applicationEventPublisher.publishEvent(
            OrderPaymentAuditEvent(
                eventType = OrderPaymentAuditEventType.PAYMENT_FAILED,
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
