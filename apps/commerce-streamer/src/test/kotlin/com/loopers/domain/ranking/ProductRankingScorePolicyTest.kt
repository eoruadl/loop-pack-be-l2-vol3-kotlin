package com.loopers.domain.ranking

import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.messaging.order.OrderEventMessage
import com.loopers.messaging.order.OrderEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class ProductRankingScorePolicyTest {
    @Test
    fun `주문 1건 점수는 좋아요 3건보다 크다`() {
        val orderScore = ProductRankingScorePolicy.orderScores(
            OrderEventMessage(
                eventId = "order-1",
                eventType = OrderEventType.PAYMENT_SUCCEEDED,
                orderId = 1L,
                paymentId = 1L,
                userId = 1L,
                occurredAt = ZonedDateTime.now(),
                items = listOf(OrderEventMessage.OrderEventItem(productId = 1L, quantity = 1L, unitPrice = 10_000L)),
            ),
        ).getValue(1L)

        val likesScore = (1..3)
            .sumOf {
                ProductRankingScorePolicy.catalogScore(
                    CatalogEventMessage(
                        eventId = "like-$it",
                        eventType = CatalogEventType.PRODUCT_LIKED,
                        productId = 2L,
                        actorLoginId = "user$it",
                        occurredAt = ZonedDateTime.now(),
                    ),
                )
            }

        assertThat(orderScore).isGreaterThan(likesScore)
    }
}
