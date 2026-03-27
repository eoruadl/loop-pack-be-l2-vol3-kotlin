package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.outbox.OutboxEventService
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class OutboxEventPublisher(
    private val outboxEventService: OutboxEventService,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishPendingEvents() {
        outboxEventService.getPublishableBatch().forEach { event ->
            runCatching {
                val payload = objectMapper.readTree(event.payload)
                if (event.partition != null) {
                    kafkaTemplate.send(event.topic, event.partition!!, event.eventKey, payload).get()
                } else {
                    kafkaTemplate.send(event.topic, event.eventKey, payload).get()
                }
                outboxEventService.markPublished(event)
            }.onFailure { throwable ->
                outboxEventService.markFailed(event, throwable.message ?: "Kafka publish failed")
                log.warn(
                    "Outbox publish failed - eventId={}, topic={}, reason={}",
                    event.eventId,
                    event.topic,
                    throwable.message,
                )
            }
        }
    }
}
