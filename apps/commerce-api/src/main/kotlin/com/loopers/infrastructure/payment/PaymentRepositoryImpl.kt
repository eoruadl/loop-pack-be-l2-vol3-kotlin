package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgTransactionId
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(payment: PaymentModel): PaymentModel =
        paymentJpaRepository.save(payment)

    override fun findById(id: Long): PaymentModel? =
        paymentJpaRepository.findById(id).orElse(null)

    override fun findByOrderId(orderId: Long): PaymentModel? =
        paymentJpaRepository.findByOrderId(orderId)

    override fun existsActiveByOrderId(orderId: Long): Boolean =
        paymentJpaRepository.existsByOrderIdAndStatusIn(
            orderId,
            listOf(PaymentStatus.PENDING, PaymentStatus.COMPLETED),
        )

    override fun findByPgTransactionId(pgTransactionId: String): PaymentModel? =
        paymentJpaRepository.findByPgTxId(PgTransactionId(pgTransactionId))

    override fun findPendingOlderThan(threshold: ZonedDateTime): List<PaymentModel> =
        paymentJpaRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold)
}
