package com.loopers.infrastructure.idempotency

import com.loopers.domain.idempotency.EventHandledModel
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandledModel, Long> {
    fun existsByEventId(eventId: String): Boolean
}
