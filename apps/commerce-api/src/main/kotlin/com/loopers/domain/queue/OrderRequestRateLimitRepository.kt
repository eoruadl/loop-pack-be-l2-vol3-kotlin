package com.loopers.domain.queue

import java.time.Duration

interface OrderRequestRateLimitRepository {
    fun increment(userId: Long, window: Duration): Long

    fun getRetryAfterSeconds(userId: Long): Long
}
