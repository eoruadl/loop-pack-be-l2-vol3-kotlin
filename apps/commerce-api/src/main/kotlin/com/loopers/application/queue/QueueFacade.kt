package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueService
import com.loopers.domain.queue.OrderQueueTokenService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

@Component
class QueueFacade(
    private val userService: UserService,
    private val orderQueueService: OrderQueueService,
    private val orderQueueTokenService: OrderQueueTokenService,
    private val queueAdmissionProperties: QueueAdmissionProperties,
) {
    fun enter(loginId: String): QueueInfo {
        val user = userService.getUserByLoginId(loginId)
        val activeToken = orderQueueTokenService.getActiveToken(user.id)
        if (activeToken != null) {
            val recommendedPollIntervalSeconds = calculateRecommendedPollIntervalSeconds(position = 0L)
            return QueueInfo.from(
                activeToken,
                orderQueueService.getWaitingCount(),
                estimatedWaitSeconds = 0L,
                retryAfterSeconds = calculateRetryAfterSeconds(activeToken.usableAt),
                recommendedPollIntervalSeconds = recommendedPollIntervalSeconds,
            )
        }

        val waitingEntry = orderQueueService.getWaitingQueueEntryOrNull(user.id)
        if (waitingEntry != null) {
            val estimatedWaitSeconds = calculateEstimatedWaitSeconds(waitingEntry.position)
            return QueueInfo.from(
                waitingEntry,
                entered = false,
                estimatedWaitSeconds = estimatedWaitSeconds,
                retryAfterSeconds = estimatedWaitSeconds,
                recommendedPollIntervalSeconds = calculateRecommendedPollIntervalSeconds(waitingEntry.position),
            )
        }

        val entered = orderQueueService.enterWaitingQueue(user.id)
        val entry = orderQueueService.getWaitingQueueEntry(user.id)
        val estimatedWaitSeconds = calculateEstimatedWaitSeconds(entry.position)
        return QueueInfo.from(
            entry,
            entered,
            estimatedWaitSeconds,
            estimatedWaitSeconds,
            calculateRecommendedPollIntervalSeconds(entry.position),
        )
    }

    fun getPosition(loginId: String): QueueInfo {
        val user = userService.getUserByLoginId(loginId)
        val activeToken = orderQueueTokenService.getActiveToken(user.id)
        if (activeToken != null) {
            val recommendedPollIntervalSeconds = calculateRecommendedPollIntervalSeconds(position = 0L)
            return QueueInfo.from(
                activeToken,
                orderQueueService.getWaitingCount(),
                estimatedWaitSeconds = 0L,
                retryAfterSeconds = calculateRetryAfterSeconds(activeToken.usableAt),
                recommendedPollIntervalSeconds = recommendedPollIntervalSeconds,
            )
        }

        val waitingEntry = orderQueueService.getWaitingQueueEntryOrNull(user.id)
        if (waitingEntry != null) {
            val estimatedWaitSeconds = calculateEstimatedWaitSeconds(waitingEntry.position)
            return QueueInfo.from(
                waitingEntry,
                entered = false,
                estimatedWaitSeconds = estimatedWaitSeconds,
                retryAfterSeconds = estimatedWaitSeconds,
                recommendedPollIntervalSeconds = calculateRecommendedPollIntervalSeconds(waitingEntry.position),
            )
        }
        throw CoreException(ErrorType.NOT_FOUND, "대기열에 진입한 사용자가 아닙니다.")
    }

    private fun calculateEstimatedWaitSeconds(position: Long): Long {
        if (position <= 0) return 0L

        val batchNumber = ceil(position.toDouble() / queueAdmissionProperties.batchSize.toDouble()).toLong()
        val estimatedWaitMillis = batchNumber * queueAdmissionProperties.fixedDelayMillis
        return ceil(estimatedWaitMillis.toDouble() / 1000.0).toLong()
    }

    private fun calculateRetryAfterSeconds(usableAt: Instant): Long {
        val millis = max(Duration.between(Instant.now(), usableAt).toMillis(), 0L)
        return ceil(millis.toDouble() / 1000.0).toLong()
    }

    private fun calculateRecommendedPollIntervalSeconds(position: Long): Long =
        when {
            position <= 0L -> 1L
            position <= 20L -> 1L
            position <= 100L -> 2L
            position <= 500L -> 5L
            else -> 10L
        }
}
