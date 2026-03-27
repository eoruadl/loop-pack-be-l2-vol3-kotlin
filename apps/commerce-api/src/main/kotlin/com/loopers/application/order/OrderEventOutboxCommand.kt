package com.loopers.application.order

import com.loopers.messaging.order.OrderEventType
import java.time.ZonedDateTime

data class OrderEventOutboxCommand(
    val eventType: OrderEventType,
    val orderId: Long,
    val paymentId: Long,
    val userId: Long,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
