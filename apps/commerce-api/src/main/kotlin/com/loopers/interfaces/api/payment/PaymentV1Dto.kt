package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.domain.payment.PaymentStatus
import java.time.ZonedDateTime

class PaymentV1Dto {

    data class CreatePaymentRequest(
        val orderId: Long,
        val cardType: String,
        val cardNo: String,
    )

    data class PgCallbackRequest(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    )

    data class PaymentResponse(
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
            fun from(info: PaymentInfo) = PaymentResponse(
                id = info.id,
                orderId = info.orderId,
                userId = info.userId,
                cardType = info.cardType,
                cardNo = info.cardNo,
                status = info.status,
                pgTransactionId = info.pgTransactionId,
                createdAt = info.createdAt,
                updatedAt = info.updatedAt,
            )
        }
    }
}
