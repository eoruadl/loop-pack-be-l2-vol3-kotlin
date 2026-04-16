package com.loopers.domain.idempotency

interface EventHandledRepository {
    fun existsByEventIdAndHandlerName(eventId: String, handlerName: String): Boolean
    fun save(model: EventHandledModel): EventHandledModel
}
