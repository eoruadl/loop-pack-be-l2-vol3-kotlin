package com.loopers.domain.idempotency

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventHandledService(
    private val eventHandledRepository: EventHandledRepository,
) {
    @Transactional(readOnly = true)
    fun isHandled(eventId: String): Boolean =
        eventHandledRepository.existsByEventId(eventId)

    @Transactional
    fun markHandled(eventId: String, topic: String): EventHandledModel =
        eventHandledRepository.save(EventHandledModel(eventId = eventId, topic = topic))
}
