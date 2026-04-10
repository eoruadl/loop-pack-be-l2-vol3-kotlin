package com.loopers.domain.ranking

import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.messaging.order.OrderEventMessage
import kotlin.math.ln

object ProductRankingScorePolicy {
    private const val VIEW_WEIGHT = 0.1
    private const val LIKE_WEIGHT = 0.2
    private const val ORDER_WEIGHT = 0.6

    fun catalogScore(event: CatalogEventMessage): Double = when (event.eventType) {
        CatalogEventType.PRODUCT_VIEWED -> VIEW_WEIGHT
        CatalogEventType.PRODUCT_LIKED -> LIKE_WEIGHT
        CatalogEventType.PRODUCT_UNLIKED -> -LIKE_WEIGHT
    }

    fun orderScores(event: OrderEventMessage): Map<Long, Double> =
        event.items.groupBy { it.productId }
            .mapValues { (_, items) ->
                items.sumOf { item -> ORDER_WEIGHT * ln(1 + (item.unitPrice * item.quantity).toDouble()) }
            }
}
