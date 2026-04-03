package com.loopers.domain.queue

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OrderQueueService(
    private val orderQueueRepository: OrderQueueRepository,
) {
    fun enterWaitingQueue(userId: Long): Boolean =
        orderQueueRepository.enterWaitingQueue(userId, Instant.now())

    fun getWaitingQueueEntry(userId: Long): OrderQueueEntry =
        orderQueueRepository.getWaitingQueueEntry(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "대기열에 진입한 사용자가 아닙니다.")

    fun getWaitingQueueEntryOrNull(userId: Long): OrderQueueEntry? =
        orderQueueRepository.getWaitingQueueEntry(userId)

    fun getWaitingUserIds(limit: Long): List<Long> =
        orderQueueRepository.getWaitingUserIds(limit)

    fun removeWaitingUsers(userIds: List<Long>): Long =
        orderQueueRepository.removeWaitingUsers(userIds)

    fun getWaitingCount(): Long =
        orderQueueRepository.getWaitingCount()
}
