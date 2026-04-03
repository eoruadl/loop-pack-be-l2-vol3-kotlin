package com.loopers.domain.queue

import com.loopers.application.queue.QueueAdmissionProperties
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class OrderQueueTokenService(
    private val orderQueueTokenRepository: OrderQueueTokenRepository,
    private val queueAdmissionProperties: QueueAdmissionProperties,
) {
    fun issueToken(
        userId: Long,
        usableAt: Instant = Instant.now(),
        ttl: Duration = queueAdmissionProperties.tokenTtl,
    ): ActiveQueueToken =
        orderQueueTokenRepository.issueToken(userId, Instant.now(), usableAt, ttl)

    fun getActiveToken(userId: Long): ActiveQueueToken? =
        orderQueueTokenRepository.getActiveToken(userId)

    fun consumeIfValid(userId: Long, token: String, now: Instant = Instant.now()): TokenConsumeResult =
        orderQueueTokenRepository.consumeIfValid(
            userId = userId,
            token = token,
            now = now,
            consumedTtl = queueAdmissionProperties.tokenTtl,
        )

    fun matchesActiveToken(userId: Long, token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val activeToken = orderQueueTokenRepository.getActiveToken(userId) ?: return false
        return activeToken.token == token
    }

    fun hasActiveToken(userId: Long): Boolean =
        orderQueueTokenRepository.hasActiveToken(userId)

    fun revokeToken(userId: Long): Boolean =
        orderQueueTokenRepository.revokeToken(userId)
}
