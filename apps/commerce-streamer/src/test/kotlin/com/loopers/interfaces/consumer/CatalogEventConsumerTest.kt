package com.loopers.interfaces.consumer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.idempotency.EventHandledService
import com.loopers.domain.metrics.ProductMetricsService
import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.support.Acknowledgment
import java.time.ZonedDateTime

class CatalogEventConsumerTest {

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val eventHandledService: EventHandledService = mock()
    private val productMetricsService: ProductMetricsService = mock()
    private val acknowledgment: Acknowledgment = mock()
    private val consumer = CatalogEventConsumer(
        objectMapper = objectMapper,
        eventHandledService = eventHandledService,
        productMetricsService = productMetricsService,
        topicName = "catalog-events",
    )

    @Test
    fun `처음 수신한 이벤트면 집계 반영 후 handled 처리한다`() {
        val event = CatalogEventMessage(
            eventId = "evt-1",
            eventType = CatalogEventType.PRODUCT_LIKED,
            productId = 1L,
            actorLoginId = "testuser",
            occurredAt = ZonedDateTime.now(),
        )
        whenever(eventHandledService.isHandled("evt-1")).thenReturn(false)

        consumer.consume(recordOf(event), acknowledgment)

        verify(productMetricsService).apply(
            argThat {
                eventId == "evt-1" &&
                    eventType == CatalogEventType.PRODUCT_LIKED &&
                    productId == 1L &&
                    actorLoginId == "testuser"
            }
        )
        verify(eventHandledService).markHandled("evt-1", "catalog-events")
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `이미 처리한 이벤트면 집계를 다시 반영하지 않는다`() {
        val event = CatalogEventMessage(
            eventId = "evt-2",
            eventType = CatalogEventType.PRODUCT_VIEWED,
            productId = 1L,
            actorLoginId = null,
            occurredAt = ZonedDateTime.now(),
        )
        whenever(eventHandledService.isHandled("evt-2")).thenReturn(true)

        consumer.consume(recordOf(event), acknowledgment)

        verify(productMetricsService, never()).apply(org.mockito.kotlin.any())
        verify(acknowledgment).acknowledge()
    }

    private fun recordOf(event: CatalogEventMessage): ConsumerRecord<String, ByteArray> =
        ConsumerRecord("catalog-events", 0, 0L, event.productId.toString(), objectMapper.writeValueAsBytes(event))
}
