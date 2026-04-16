package com.loopers.domain.idempotency

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventHandledService(
    private val eventHandledRepository: EventHandledRepository,
) {
    @Transactional(readOnly = true)
    fun isHandled(eventId: String, handlerName: String): Boolean =
        eventHandledRepository.existsByEventIdAndHandlerName(eventId, handlerName)

    @Transactional
    fun markHandled(eventId: String, topic: String, handlerName: String): EventHandledModel =
        eventHandledRepository.save(EventHandledModel(eventId = eventId, topic = topic, handlerName = handlerName))
}
