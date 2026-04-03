package com.loopers.application.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventService
import com.loopers.messaging.catalog.CatalogEventMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CatalogEventOutboxService(
    private val outboxEventService: OutboxEventService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.kafka.topics.catalog-events:catalog-events}")
    private val catalogTopic: String,
) {
    @Transactional
    fun enqueue(command: CatalogEventOutboxCommand): OutboxEventModel {
        val message = CatalogEventMessage(
            eventId = UUID.randomUUID().toString(),
            eventType = command.eventType,
            productId = command.productId,
            actorLoginId = command.actorLoginId,
            occurredAt = command.occurredAt,
        )

        return outboxEventService.save(
            OutboxEventService.SaveOutboxEventCommand(
                eventId = message.eventId,
                topic = catalogTopic,
                eventKey = message.productId.toString(),
                eventType = message.eventType.name,
                payload = objectMapper.writeValueAsString(message),
            ),
        )
    }
}
