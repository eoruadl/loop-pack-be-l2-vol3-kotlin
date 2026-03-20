package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PaymentService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.concurrent.ExecutionException

@Component
class PaymentRecoveryFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val pgPaymentPort: PgPaymentPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun recoverPayment(paymentId: Long) {
        val payment = paymentService.getPaymentById(paymentId)
        if (payment.status != PaymentStatus.PENDING) return

        val pgStatusResponse = try {
            if (payment.pgTxId != null) {
                pgPaymentPort.getPayment(payment.pgTxId!!.value, payment.userId).get()
            } else {
                pgPaymentPort.getPaymentByOrderId(payment.orderId, payment.userId).get() ?: run {
                    paymentService.failPayment(payment.id)
                    return
                }
            }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

        when (pgStatusResponse.status) {
            "SUCCESS" -> {
                paymentService.completePayment(payment.id)
                orderService.payOrder(payment.orderId)
            }
            "FAILED" -> when (pgStatusResponse.failureCode) {
                PgFailureCode.LIMIT_EXCEEDED -> paymentService.failPayment(payment.id, PaymentStatus.LIMIT_EXCEEDED)
                PgFailureCode.INVALID_CARD -> paymentService.failPayment(payment.id, PaymentStatus.INVALID_CARD)
                else -> paymentService.failPayment(payment.id)
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
}
