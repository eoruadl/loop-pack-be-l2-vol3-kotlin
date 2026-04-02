package com.loopers.infrastructure.outbox

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Profile("!test")
@Component
class OutboxEventPublisherScheduler(
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    @Scheduled(fixedDelay = 5_000)
    fun publishPendingEvents() {
        outboxEventPublisher.publishPendingEvents()
    }
}
