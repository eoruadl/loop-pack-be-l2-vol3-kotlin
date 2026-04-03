package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.OrderRequestRateLimitRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Component
class OrderRequestRateLimitRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
) : OrderRequestRateLimitRepository {
    override fun increment(userId: Long, window: Duration): Long {
        val key = rateLimitKey(userId)
        val currentCount = masterRedisTemplate.opsForValue().increment(key) ?: 0L
        if (currentCount == 1L) {
            masterRedisTemplate.expire(key, window)
        }
        return currentCount
    }

    override fun getRetryAfterSeconds(userId: Long): Long {
        val ttlSeconds = masterRedisTemplate.getExpire(rateLimitKey(userId), TimeUnit.SECONDS)
        return max(ttlSeconds, 0L)
    }

    private fun rateLimitKey(userId: Long): String = "queue:order:rate-limit:$userId"
}
