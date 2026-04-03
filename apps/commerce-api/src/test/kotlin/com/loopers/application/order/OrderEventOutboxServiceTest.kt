package com.loopers.application.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.loopers.domain.order.ImageUrl
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.Price
import com.loopers.domain.order.ProductName
import com.loopers.domain.order.Quantity
import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventService
import com.loopers.messaging.order.OrderEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OrderEventOutboxServiceTest {

    private val orderItemService: OrderItemService = mock()
    private val outboxEventService: OutboxEventService = mock()
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
    private val service = OrderEventOutboxService(orderItemService, outboxEventService, objectMapper, "order-events")

    @Test
    fun `주문 이벤트를 아웃박스에 저장한다`() {
        whenever(orderItemService.getItemsByOrderId(1L)).thenReturn(
            listOf(
                OrderItemModel(
                    orderId = 1L,
                    brandId = 1L,
                    productId = 1L,
                    quantity = Quantity(2L),
                    unitPrice = Price(10_000L),
                    productName = ProductName("상품"),
                    imageUrl = ImageUrl("image.png"),
                ),
            ),
        )
        whenever(outboxEventService.save(any())).thenAnswer {
            val command = it.getArgument<OutboxEventService.SaveOutboxEventCommand>(0)
            OutboxEventModel(
                eventId = command.eventId,
                topic = command.topic,
                eventKey = command.eventKey,
                eventType = command.eventType,
                payload = command.payload,
            )
        }

        val saved = service.enqueue(
            OrderEventOutboxCommand(
                eventType = OrderEventType.PAYMENT_SUCCEEDED,
                orderId = 1L,
                paymentId = 10L,
                userId = 1L,
            ),
        )

        assertThat(saved.topic).isEqualTo("order-events")
        assertThat(saved.eventKey).isEqualTo("1")
        assertThat(saved.eventType).isEqualTo(OrderEventType.PAYMENT_SUCCEEDED.name)
        assertThat(saved.payload).contains("PAYMENT_SUCCEEDED")
        assertThat(saved.payload).contains("\"productId\":1")
    }
}
