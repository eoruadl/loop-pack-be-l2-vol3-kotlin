package com.loopers.infrastructure.idempotency

import com.loopers.domain.idempotency.EventHandledModel
import com.loopers.domain.idempotency.EventHandledRepository
import org.springframework.stereotype.Repository

@Repository
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun existsByEventIdAndHandlerName(eventId: String, handlerName: String): Boolean =
        eventHandledJpaRepository.existsByEventIdAndHandlerName(eventId, handlerName)

    override fun save(model: EventHandledModel): EventHandledModel =
        eventHandledJpaRepository.save(model)
}
