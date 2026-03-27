package com.loopers.interfaces.consumer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.idempotency.EventHandledService
import com.loopers.domain.metrics.ProductMetricsService
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

class OrderEventConsumerTest {

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val eventHandledService: EventHandledService = mock()
    private val productMetricsService: ProductMetricsService = mock()
    private val acknowledgment: Acknowledgment = mock()
    private val consumer = OrderEventConsumer(
        objectMapper = objectMapper,
        eventHandledService = eventHandledService,
        productMetricsService = productMetricsService,
        topicName = "order-events",
    )

    @Test
    fun `처음 수신한 주문 이벤트면 판매 집계를 반영한다`() {
        val event = OrderEventMessage(
            eventId = "order-evt-1",
            eventType = OrderEventType.PAYMENT_SUCCEEDED,
            orderId = 100L,
            paymentId = 10L,
            userId = 1L,
            occurredAt = ZonedDateTime.now(),
            items = listOf(OrderEventMessage.OrderEventItem(productId = 1L, quantity = 2L)),
        )
        whenever(eventHandledService.isHandled("order-evt-1")).thenReturn(false)

        consumer.consume(recordOf(event), acknowledgment)

        verify(productMetricsService).applySales(
            argThat {
                eventId == "order-evt-1" &&
                    eventType == OrderEventType.PAYMENT_SUCCEEDED &&
                    orderId == 100L
            }
        )
        verify(eventHandledService).markHandled("order-evt-1", "order-events")
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `이미 처리한 주문 이벤트면 판매 집계를 다시 반영하지 않는다`() {
        val event = OrderEventMessage(
            eventId = "order-evt-2",
            eventType = OrderEventType.PAYMENT_RECOVERED,
            orderId = 101L,
            paymentId = 11L,
            userId = 1L,
            occurredAt = ZonedDateTime.now(),
            items = listOf(OrderEventMessage.OrderEventItem(productId = 1L, quantity = 1L)),
        )
        whenever(eventHandledService.isHandled("order-evt-2")).thenReturn(true)

        consumer.consume(recordOf(event), acknowledgment)

        verify(productMetricsService, never()).applySales(org.mockito.kotlin.any())
        verify(acknowledgment).acknowledge()
    }

    private fun recordOf(event: OrderEventMessage): ConsumerRecord<String, ByteArray> =
        ConsumerRecord("order-events", 0, 0L, event.orderId.toString(), objectMapper.writeValueAsBytes(event))
}
