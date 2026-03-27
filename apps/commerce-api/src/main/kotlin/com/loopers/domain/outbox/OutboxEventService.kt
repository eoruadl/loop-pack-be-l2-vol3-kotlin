package com.loopers.domain.outbox

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxEventService(
    private val outboxEventRepository: OutboxEventRepository,
) {
    @Transactional
    fun save(command: SaveOutboxEventCommand): OutboxEventModel =
        outboxEventRepository.save(
            OutboxEventModel(
                eventId = command.eventId,
                topic = command.topic,
                partition = command.partition,
                eventKey = command.eventKey,
                eventType = command.eventType,
                payload = command.payload,
            )
        )

    @Transactional(readOnly = true)
    fun getPublishableBatch(limit: Int = 100): List<OutboxEventModel> =
        outboxEventRepository.findTop100ByStatusInOrderByCreatedAtAsc(
            listOf(OutboxEventStatus.PENDING, OutboxEventStatus.FAILED),
        ).take(limit)

    @Transactional
    fun markPublished(event: OutboxEventModel): OutboxEventModel {
        event.markPublished()
        return outboxEventRepository.save(event)
    }

    @Transactional
    fun markFailed(event: OutboxEventModel, reason: String): OutboxEventModel {
        event.markFailed(reason)
        return outboxEventRepository.save(event)
    }

    data class SaveOutboxEventCommand(
        val eventId: String,
        val topic: String,
        val partition: Int? = null,
        val eventKey: String,
        val eventType: String,
        val payload: String,
    )
}
