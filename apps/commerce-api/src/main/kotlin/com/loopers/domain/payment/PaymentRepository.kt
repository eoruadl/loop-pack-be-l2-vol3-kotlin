package com.loopers.domain.payment

import java.time.ZonedDateTime

interface PaymentRepository {
    fun save(payment: PaymentModel): PaymentModel
    fun findById(id: Long): PaymentModel?
    fun findByOrderId(orderId: Long): PaymentModel?
    fun existsActiveByOrderId(orderId: Long): Boolean
    fun findByPgTransactionId(pgTransactionId: String): PaymentModel?
    fun findPendingOlderThan(threshold: ZonedDateTime): List<PaymentModel>
}
