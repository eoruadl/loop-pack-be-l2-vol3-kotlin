package com.loopers.messaging.order

import java.time.ZonedDateTime

data class OrderEventMessage(
    val eventId: String,
    val eventType: OrderEventType,
    val orderId: Long,
    val paymentId: Long,
    val userId: Long,
    val occurredAt: ZonedDateTime,
    val items: List<OrderEventItem>,
) {
    data class OrderEventItem(
        val productId: Long,
        val quantity: Long,
    )
}
