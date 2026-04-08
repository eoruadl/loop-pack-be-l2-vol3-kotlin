package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueInfo
import java.time.ZonedDateTime

class QueueV1Dto {
    data class QueueResponse(
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
            fun from(info: QueueInfo) = QueueResponse(
                userId = info.userId,
                position = info.position,
                waitingCount = info.waitingCount,
                entered = info.entered,
                enteredAt = info.enteredAt,
                status = info.status,
                estimatedWaitSeconds = info.estimatedWaitSeconds,
                queueToken = info.queueToken,
                retryAfterSeconds = info.retryAfterSeconds,
                recommendedPollIntervalSeconds = info.recommendedPollIntervalSeconds,
            )
        }
    }
}
