package com.loopers.domain.idempotency

interface EventHandledRepository {
    fun existsByEventId(eventId: String): Boolean
    fun save(model: EventHandledModel): EventHandledModel
}
