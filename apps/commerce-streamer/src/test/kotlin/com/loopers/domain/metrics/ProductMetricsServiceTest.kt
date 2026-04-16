package com.loopers.domain.metrics

import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.messaging.order.OrderEventMessage
import com.loopers.messaging.order.OrderEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.ZonedDateTime

class ProductMetricsServiceTest {

    private val repository: ProductMetricsRepository = mock()
    private val service = ProductMetricsService(repository)

    @Test
    fun `상품 조회 이벤트를 반영하면 view count가 증가한다`() {
        whenever(repository.findByProductId(1L)).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.apply(
            CatalogEventMessage(
                eventId = "evt-1",
                eventType = CatalogEventType.PRODUCT_VIEWED,
                productId = 1L,
                actorLoginId = null,
                occurredAt = ZonedDateTime.now(),
            )
        )

        assertThat(result.viewCount).isEqualTo(1L)
        assertThat(result.likeCount).isEqualTo(0L)
    }

    @Test
    fun `상품 좋아요 취소 이벤트를 반영하면 like count가 감소한다`() {
        val existing = ProductMetricsModel(1L).apply { increaseLikeCount(ZonedDateTime.now()) }
        whenever(repository.findByProductId(1L)).thenReturn(existing)
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.apply(
            CatalogEventMessage(
                eventId = "evt-2",
                eventType = CatalogEventType.PRODUCT_UNLIKED,
                productId = 1L,
                actorLoginId = "testuser",
                occurredAt = ZonedDateTime.now(),
            )
        )

        assertThat(result.likeCount).isEqualTo(0L)
    }

    @Test
    fun `주문 결제 성공 이벤트를 반영하면 sales count가 증가한다`() {
        whenever(repository.findByProductId(1L)).thenReturn(null)
        whenever(repository.findByProductId(2L)).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.applySales(
            OrderEventMessage(
                eventId = "order-evt-1",
                eventType = OrderEventType.PAYMENT_SUCCEEDED,
                orderId = 100L,
                paymentId = 10L,
                userId = 1L,
                occurredAt = ZonedDateTime.now(),
                items = listOf(
                    OrderEventMessage.OrderEventItem(productId = 1L, quantity = 2L, unitPrice = 10_000L),
                    OrderEventMessage.OrderEventItem(productId = 2L, quantity = 1L, unitPrice = 20_000L),
                ),
            )
        )

        assertThat(result).hasSize(2)
        assertThat(result.first { it.productId == 1L }.salesCount).isEqualTo(2L)
        assertThat(result.first { it.productId == 2L }.salesCount).isEqualTo(1L)
    }
}
