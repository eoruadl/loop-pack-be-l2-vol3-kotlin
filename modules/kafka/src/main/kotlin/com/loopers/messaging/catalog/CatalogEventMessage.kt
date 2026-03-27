package com.loopers.messaging.catalog

import java.time.ZonedDateTime

data class CatalogEventMessage(
    val eventId: String,
    val eventType: CatalogEventType,
    val productId: Long,
    val actorLoginId: String?,
    val occurredAt: ZonedDateTime,
)
