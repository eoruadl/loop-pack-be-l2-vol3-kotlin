package com.loopers.domain.payment

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    LIMIT_EXCEEDED,
    INVALID_CARD,
}
