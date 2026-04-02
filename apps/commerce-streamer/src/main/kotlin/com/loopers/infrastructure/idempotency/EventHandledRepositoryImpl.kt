package com.loopers.infrastructure.idempotency

import com.loopers.domain.idempotency.EventHandledModel
import com.loopers.domain.idempotency.EventHandledRepository
import org.springframework.stereotype.Repository

@Repository
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun existsByEventId(eventId: String): Boolean =
        eventHandledJpaRepository.existsByEventId(eventId)

    override fun save(model: EventHandledModel): EventHandledModel =
        eventHandledJpaRepository.save(model)
}
