package com.loopers.domain.queue

import com.loopers.application.queue.QueueAdmissionProperties
import org.springframework.stereotype.Service

@Service
class OrderRequestRateLimitService(
    private val orderRequestRateLimitRepository: OrderRequestRateLimitRepository,
    private val queueAdmissionProperties: QueueAdmissionProperties,
) {
    fun checkLimit(userId: Long): RateLimitCheckResult {
        val currentCount = orderRequestRateLimitRepository.increment(userId, queueAdmissionProperties.orderRateLimitWindow)
        val allowed = currentCount <= queueAdmissionProperties.orderRateLimitMaxRequests
        val retryAfterSeconds = if (allowed) 0L else orderRequestRateLimitRepository.getRetryAfterSeconds(userId)
        return RateLimitCheckResult(
            allowed = allowed,
            retryAfterSeconds = retryAfterSeconds,
        )
    }
}

data class RateLimitCheckResult(
    val allowed: Boolean,
    val retryAfterSeconds: Long,
)
