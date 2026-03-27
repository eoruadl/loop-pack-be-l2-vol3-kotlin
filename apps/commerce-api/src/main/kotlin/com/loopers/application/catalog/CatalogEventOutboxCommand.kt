package com.loopers.application.catalog

import com.loopers.messaging.catalog.CatalogEventType
import java.time.ZonedDateTime

data class CatalogEventOutboxCommand(
    val eventType: CatalogEventType,
    val productId: Long,
    val actorLoginId: String?,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
