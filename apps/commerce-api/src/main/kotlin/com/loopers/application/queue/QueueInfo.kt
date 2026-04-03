package com.loopers.application.queue

import com.loopers.domain.queue.ActiveQueueToken
import com.loopers.domain.queue.OrderQueueEntry
import com.loopers.domain.queue.QueueStatus
import java.time.ZonedDateTime

data class QueueInfo(
    val userId: Long,
    val position: Long,
    val waitingCount: Long,
    val entered: Boolean,
    val enteredAt: ZonedDateTime,
    val status: String,
    val estimatedWaitSeconds: Long,
    val queueToken: String?,
    val retryAfterSeconds: Long,
    val recommendedPollIntervalSeconds: Long,
) {
    companion object {
        fun from(
            entry: OrderQueueEntry,
            entered: Boolean,
            estimatedWaitSeconds: Long,
            retryAfterSeconds: Long,
            recommendedPollIntervalSeconds: Long,
        ) = QueueInfo(
            userId = entry.userId,
            position = entry.position,
            waitingCount = entry.waitingCount,
            entered = entered,
            enteredAt = ZonedDateTime.ofInstant(entry.enteredAt, ZonedDateTime.now().zone),
            status = entry.status.name,
            estimatedWaitSeconds = estimatedWaitSeconds,
            queueToken = null,
            retryAfterSeconds = retryAfterSeconds,
            recommendedPollIntervalSeconds = recommendedPollIntervalSeconds,
        )

        fun from(
            activeToken: ActiveQueueToken,
            waitingCount: Long,
            estimatedWaitSeconds: Long,
            retryAfterSeconds: Long,
            recommendedPollIntervalSeconds: Long,
        ) = QueueInfo(
            userId = activeToken.userId,
            position = 0L,
            waitingCount = waitingCount,
            entered = false,
            enteredAt = ZonedDateTime.ofInstant(activeToken.issuedAt, ZonedDateTime.now().zone),
            status = QueueStatus.ALLOWED.name,
            estimatedWaitSeconds = estimatedWaitSeconds,
            queueToken = activeToken.token,
            retryAfterSeconds = retryAfterSeconds,
            recommendedPollIntervalSeconds = recommendedPollIntervalSeconds,
        )
    }
}
