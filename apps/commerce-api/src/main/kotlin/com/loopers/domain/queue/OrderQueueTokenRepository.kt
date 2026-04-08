package com.loopers.domain.queue

import java.time.Duration
import java.time.Instant

interface OrderQueueTokenRepository {
    fun issueToken(userId: Long, issuedAt: Instant, usableAt: Instant, ttl: Duration): ActiveQueueToken

    fun getActiveToken(userId: Long): ActiveQueueToken?

    fun consumeIfValid(userId: Long, token: String, now: Instant, consumedTtl: Duration): TokenConsumeResult

    fun hasActiveToken(userId: Long): Boolean

    fun revokeToken(userId: Long): Boolean
}

data class ActiveQueueToken(
    val userId: Long,
    val token: String,
    val issuedAt: Instant,
    val usableAt: Instant,
    val expiresAt: Instant,
)

sealed interface TokenConsumeResult {
    data object Success : TokenConsumeResult

    data object Missing : TokenConsumeResult

    data object Mismatch : TokenConsumeResult

    data object AlreadyConsumed : TokenConsumeResult

    data class TooEarly(
        val retryAfterSeconds: Long,
    ) : TokenConsumeResult
}
