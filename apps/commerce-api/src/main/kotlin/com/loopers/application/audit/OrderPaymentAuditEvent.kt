package com.loopers.application.audit

import com.loopers.domain.audit.OrderPaymentAuditEventType
import java.time.ZonedDateTime
import java.util.UUID

data class OrderPaymentAuditEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: OrderPaymentAuditEventType,
    val orderId: Long,
    val paymentId: Long? = null,
    val userId: Long,
    val orderStatus: String? = null,
    val paymentStatus: String? = null,
    val cardType: String? = null,
    val maskedCardNo: String? = null,
    val pgTransactionId: String? = null,
    val reason: String? = null,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
