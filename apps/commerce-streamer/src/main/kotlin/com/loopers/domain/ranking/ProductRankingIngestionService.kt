package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductRankingRedisRepository
import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.order.OrderEventMessage
import org.springframework.stereotype.Service

@Service
class ProductRankingIngestionService(
    private val productRankingRedisRepository: ProductRankingRedisRepository,
) {
    fun apply(event: CatalogEventMessage) {
        val score = ProductRankingScorePolicy.catalogScore(event)
        productRankingRedisRepository.incrementMinuteScore(event.productId, score, event.occurredAt)
    }

    fun apply(event: OrderEventMessage) {
        ProductRankingScorePolicy.orderScores(event)
            .forEach { (productId, score) ->
                productRankingRedisRepository.incrementMinuteScore(productId, score, event.occurredAt)
            }
    }
}
