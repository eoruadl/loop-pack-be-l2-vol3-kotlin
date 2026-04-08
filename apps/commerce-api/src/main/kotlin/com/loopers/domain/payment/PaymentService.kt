package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createPayment(orderId: Long, userId: Long, cardType: CardType, cardNo: String): PaymentModel {
        if (paymentRepository.existsActiveByOrderId(orderId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 진행 중인 결제가 있습니다.")
        }
        return paymentRepository.save(
            PaymentModel(
                orderId = orderId,
                userId = userId,
                cardType = cardType,
                cardNo = CardNo(cardNo),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getPaymentById(id: Long): PaymentModel =
        paymentRepository.findById(id) ?: throw CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.")

    @Transactional(readOnly = true)
    fun getPaymentByOrderId(orderId: Long): PaymentModel? =
        paymentRepository.findByOrderId(orderId)

    @Transactional(readOnly = true)
    fun getPaymentByPgTransactionId(pgTransactionId: String): PaymentModel =
        paymentRepository.findByPgTransactionId(pgTransactionId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "PG 트랜잭션 ID로 결제 정보를 찾을 수 없습니다.")

    @Transactional(readOnly = true)
    fun findPendingPaymentsOlderThan(threshold: ZonedDateTime): List<PaymentModel> =
        paymentRepository.findPendingOlderThan(threshold)

    @Transactional
    fun completePayment(paymentId: Long): PaymentModel {
        val payment = getPaymentById(paymentId)
        payment.complete()
        return paymentRepository.save(payment)
    }

    @Transactional
    fun failPayment(paymentId: Long, failStatus: PaymentStatus = PaymentStatus.FAILED): PaymentModel {
        val payment = getPaymentById(paymentId)
        payment.fail(failStatus)
        return paymentRepository.save(payment)
    }

    @Transactional
    fun setPgTransactionId(paymentId: Long, pgTransactionId: String): PaymentModel {
        val payment = getPaymentById(paymentId)
        payment.setPgTransactionId(PgTransactionId(pgTransactionId))
        return paymentRepository.save(payment)
    }
}
