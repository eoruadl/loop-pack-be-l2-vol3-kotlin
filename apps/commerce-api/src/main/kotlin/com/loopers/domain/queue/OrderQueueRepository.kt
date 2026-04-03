package com.loopers.domain.queue

import java.time.Instant

interface OrderQueueRepository {
    fun enterWaitingQueue(userId: Long, enteredAt: Instant): Boolean

    fun getWaitingQueueEntry(userId: Long): OrderQueueEntry?

    fun getWaitingUserIds(limit: Long): List<Long>

    fun removeWaitingUsers(userIds: List<Long>): Long

    fun getWaitingCount(): Long
}

data class OrderQueueEntry(
    val userId: Long,
    val position: Long,
    val waitingCount: Long,
    val enteredAt: Instant,
    val status: QueueStatus,
)

enum class QueueStatus {
    WAITING,
    ALLOWED,
}
