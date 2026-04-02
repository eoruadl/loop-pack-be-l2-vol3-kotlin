package com.loopers.domain.outbox

interface OutboxEventRepository {
    fun save(event: OutboxEventModel): OutboxEventModel
    fun findTop100ByStatusInOrderByCreatedAtAsc(statuses: List<OutboxEventStatus>): List<OutboxEventModel>
}
