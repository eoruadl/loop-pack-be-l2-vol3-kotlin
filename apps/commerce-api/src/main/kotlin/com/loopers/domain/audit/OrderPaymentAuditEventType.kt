package com.loopers.domain.audit

enum class OrderPaymentAuditEventType {
    ORDER_PLACED,
    PAYMENT_REQUESTED,
    PAYMENT_REQUEST_FAILED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PAYMENT_RECOVERED,
    PAYMENT_RECOVERY_FAILED,
}
