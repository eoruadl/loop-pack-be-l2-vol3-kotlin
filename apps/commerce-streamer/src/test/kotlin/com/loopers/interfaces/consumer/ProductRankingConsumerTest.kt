package com.loopers.interfaces.consumer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.idempotency.EventHandledService
import com.loopers.domain.ranking.ProductRankingIngestionService
import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.messaging.order.OrderEventMessage
import com.loopers.messaging.order.OrderEventType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.support.Acknowledgment
import java.time.ZonedDateTime

class ProductRankingConsumerTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val eventHandledService: EventHandledService = mock()
    private val productRankingIngestionService: ProductRankingIngestionService = mock()
    private val acknowledgment: Acknowledgment = mock()
    private val consumer = ProductRankingConsumer(
        objectMapper = objectMapper,
        eventHandledService = eventHandledService,
        productRankingIngestionService = productRankingIngestionService,
        catalogTopicName = "catalog-events",
        orderTopicName = "order-events",
    )

    @Test
    fun `새 catalog 이벤트면 ranking 적재 후 handler별 handled 처리한다`() {
        val event = CatalogEventMessage(
            eventId = "evt-1",
            eventType = CatalogEventType.PRODUCT_VIEWED,
            productId = 1L,
            actorLoginId = null,
            occurredAt = ZonedDateTime.now(),
        )
        whenever(eventHandledService.isHandled("evt-1", ProductRankingConsumer.CATALOG_HANDLER_NAME)).thenReturn(false)

        consumer.consumeCatalog(recordOf(event), acknowledgment)

        verify(productRankingIngestionService).apply(
            argThat<CatalogEventMessage> { eventId == "evt-1" && productId == 1L },
        )
        verify(eventHandledService).markHandled("evt-1", "catalog-events", ProductRankingConsumer.CATALOG_HANDLER_NAME)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `이미 처리한 order ranking 이벤트면 다시 적재하지 않는다`() {
        val event = OrderEventMessage(
            eventId = "order-evt-1",
            eventType = OrderEventType.PAYMENT_SUCCEEDED,
            orderId = 10L,
            paymentId = 20L,
            userId = 30L,
            occurredAt = ZonedDateTime.now(),
            items = listOf(OrderEventMessage.OrderEventItem(productId = 1L, quantity = 1L, unitPrice = 10_000L)),
        )
        whenever(eventHandledService.isHandled("order-evt-1", ProductRankingConsumer.ORDER_HANDLER_NAME)).thenReturn(true)

        consumer.consumeOrder(orderRecordOf(event), acknowledgment)

        verify(productRankingIngestionService, never()).apply(org.mockito.kotlin.any<OrderEventMessage>())
        verify(acknowledgment).acknowledge()
    }

    private fun recordOf(event: CatalogEventMessage): ConsumerRecord<String, ByteArray> =
        ConsumerRecord("catalog-events", 0, 0L, event.productId.toString(), objectMapper.writeValueAsBytes(event))

    private fun orderRecordOf(event: OrderEventMessage): ConsumerRecord<String, ByteArray> =
        ConsumerRecord("order-events", 0, 0L, event.orderId.toString(), objectMapper.writeValueAsBytes(event))
}
