package com.loopers.application.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventService
import com.loopers.messaging.order.OrderEventMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrderEventOutboxService(
    private val orderItemService: OrderItemService,
    private val outboxEventService: OutboxEventService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.kafka.topics.order-events:order-events}")
    private val orderTopic: String,
) {
    @Transactional
    fun enqueue(command: OrderEventOutboxCommand): OutboxEventModel {
        val items = orderItemService.getItemsByOrderId(command.orderId)
        val message = OrderEventMessage(
            eventId = UUID.randomUUID().toString(),
            eventType = command.eventType,
            orderId = command.orderId,
            paymentId = command.paymentId,
            userId = command.userId,
            occurredAt = command.occurredAt,
            items = items.map { item ->
                OrderEventMessage.OrderEventItem(
                    productId = item.productId,
                    quantity = item.quantity.value,
                )
            },
        )

        return outboxEventService.save(
            OutboxEventService.SaveOutboxEventCommand(
                eventId = message.eventId,
                topic = orderTopic,
                eventKey = message.orderId.toString(),
                eventType = message.eventType.name,
                payload = objectMapper.writeValueAsString(message),
            )
        )
    }
}
