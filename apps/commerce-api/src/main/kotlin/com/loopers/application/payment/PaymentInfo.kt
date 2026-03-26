package com.loopers.application.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentStatus
import java.time.ZonedDateTime

data class PaymentInfo(
    val id: Long,
    val orderId: Long,
    val userId: Long,
    val cardType: String,
    val cardNo: String,
    val status: PaymentStatus,
    val pgTransactionId: String?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        fun from(payment: PaymentModel) = PaymentInfo(
            id = payment.id,
            orderId = payment.orderId,
            userId = payment.userId,
            cardType = payment.cardType.name,
            cardNo = payment.cardNo.value,
            status = payment.status,
            pgTransactionId = payment.pgTxId?.value,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
        )
    }
}
