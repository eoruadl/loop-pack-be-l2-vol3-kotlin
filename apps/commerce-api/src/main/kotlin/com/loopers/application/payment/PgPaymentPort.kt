package com.loopers.application.payment

interface PgPaymentPort {
    fun requestPayment(request: PgPaymentRequest): PgPaymentResponse
    fun getPayment(pgTxId: String, userId: Long): PgPaymentStatusResponse
    fun getPaymentByOrderId(orderId: Long, userId: Long): PgPaymentStatusResponse?
}

data class PgPaymentRequest(
    val orderId: String,
    val userId: Long,
    val amount: Long,
    val cardType: String,
    val cardNo: String,
    val callbackUrl: String,
)

data class PgPaymentResponse(
    val pgTransactionId: String,
)

data class PgPaymentStatusResponse(
    val pgTransactionId: String,
    // PENDING | SUCCESS | FAILED
    val status: String,
    // FAILED 상태일 때만 의미 있음
    val failureCode: PgFailureCode?,
)

enum class PgFailureCode { LIMIT_EXCEEDED, INVALID_CARD, UNKNOWN }

fun parseFailureCode(reason: String?): PgFailureCode = when (reason) {
    "한도초과입니다. 다른 카드를 선택해주세요." -> PgFailureCode.LIMIT_EXCEEDED
    "잘못된 카드입니다. 다른 카드를 선택해주세요." -> PgFailureCode.INVALID_CARD
    else -> PgFailureCode.UNKNOWN
}

class PgPaymentFailException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class PgPaymentTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
