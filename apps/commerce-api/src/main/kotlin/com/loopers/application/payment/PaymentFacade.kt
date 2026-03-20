package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
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
            paymentService.setPgTransactionId(payment.id, pgResponse.pgTransactionId)
        } catch (e: PgPaymentFailException) {
            paymentService.failPayment(payment.id)
            throw CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청에 실패했습니다.")
        } catch (e: PgPaymentTimeoutException) {
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
                paymentService.completePayment(payment.id)
                orderService.payOrder(payment.orderId)
            }
            else -> when (parseFailureCode(reason)) {
                PgFailureCode.LIMIT_EXCEEDED -> paymentService.failPayment(payment.id, PaymentStatus.LIMIT_EXCEEDED)
                PgFailureCode.INVALID_CARD -> paymentService.failPayment(payment.id, PaymentStatus.INVALID_CARD)
                PgFailureCode.UNKNOWN -> paymentService.failPayment(payment.id)
            }
        }
    }
}
