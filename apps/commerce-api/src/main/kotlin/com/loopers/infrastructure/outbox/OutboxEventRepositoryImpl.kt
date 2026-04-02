package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxEventStatus
import org.springframework.stereotype.Repository

@Repository
class OutboxEventRepositoryImpl(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
) : OutboxEventRepository {
    override fun save(event: OutboxEventModel): OutboxEventModel =
        outboxEventJpaRepository.save(event)

    override fun findTop100ByStatusInOrderByCreatedAtAsc(statuses: List<OutboxEventStatus>): List<OutboxEventModel> =
        outboxEventJpaRepository.findTop100ByStatusInOrderByCreatedAtAsc(statuses)
}
