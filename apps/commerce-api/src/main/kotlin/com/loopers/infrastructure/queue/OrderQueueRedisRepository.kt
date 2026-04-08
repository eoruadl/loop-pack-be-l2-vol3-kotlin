package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.OrderQueueEntry
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.queue.QueueStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OrderQueueRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
) : OrderQueueRepository {

    companion object {
        private const val ORDER_WAITING_QUEUE_KEY = "queue:order:waiting"
    }

    override fun enterWaitingQueue(userId: Long, enteredAt: Instant): Boolean =
        masterRedisTemplate.opsForZSet()
            .addIfAbsent(ORDER_WAITING_QUEUE_KEY, userId.toString(), enteredAt.toEpochMilli().toDouble()) == true

    override fun getWaitingQueueEntry(userId: Long): OrderQueueEntry? {
        val member = userId.toString()
        val operations = masterRedisTemplate.opsForZSet()
        val rank = operations.rank(ORDER_WAITING_QUEUE_KEY, member) ?: return null
        val score = operations.score(ORDER_WAITING_QUEUE_KEY, member) ?: return null
        val waitingCount = operations.size(ORDER_WAITING_QUEUE_KEY) ?: 0L

        return OrderQueueEntry(
            userId = userId,
            position = rank + 1,
            waitingCount = waitingCount,
            enteredAt = Instant.ofEpochMilli(score.toLong()),
            status = QueueStatus.WAITING,
        )
    }

    override fun getWaitingUserIds(limit: Long): List<Long> =
        masterRedisTemplate.opsForZSet()
            .range(ORDER_WAITING_QUEUE_KEY, 0, limit - 1)
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }

    override fun removeWaitingUsers(userIds: List<Long>): Long {
        if (userIds.isEmpty()) return 0L

        return masterRedisTemplate.opsForZSet()
            .remove(ORDER_WAITING_QUEUE_KEY, *userIds.map { it.toString() }.toTypedArray()) ?: 0L
    }

    override fun getWaitingCount(): Long =
        masterRedisTemplate.opsForZSet().size(ORDER_WAITING_QUEUE_KEY) ?: 0L
}
