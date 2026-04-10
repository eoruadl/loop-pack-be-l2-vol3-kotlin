package com.loopers.infrastructure.ranking

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class ProductRankingRedisRepository(
    @Qualifier("redisTemplateMaster")
    private val masterRedisTemplate: RedisTemplate<String, String>,
) {
    data class RankingScore(
        val targetId: Long,
        val score: Double,
        val rank: Long,
    )

    data class RankingPage(
        val content: List<RankingScore>,
        val totalCount: Long,
    )

    fun incrementMinuteScore(
        productId: Long,
        score: Double,
        occurredAt: ZonedDateTime,
        ttl: Duration = Duration.ofDays(2),
    ) {
        val normalized = occurredAt.withZoneSameInstant(RankingRedisKeys.ZONE_ID).truncatedTo(ChronoUnit.MINUTES)
        val key = RankingRedisKeys.minuteBucket(normalized)
        masterRedisTemplate.opsForZSet().incrementScore(key, productId.toString(), score)
        masterRedisTemplate.expire(key, ttl)
    }

    fun materializeHourBucket(
        hour: ZonedDateTime,
        ttl: Duration = Duration.ofDays(8),
    ) {
        val normalized = hour.withZoneSameInstant(RankingRedisKeys.ZONE_ID).truncatedTo(ChronoUnit.HOURS)
        val sourceKeys = (0L until 60L).map { minute ->
            RankingRedisKeys.minuteBucket(normalized.plusMinutes(minute))
        }
        materialize(sourceKeys, RankingRedisKeys.hourBucket(normalized), ttl)
    }

    fun materializeDayBucket(
        date: LocalDate,
        ttl: Duration = Duration.ofDays(40),
    ) {
        val sourceKeys = (0L until 24L).map { hour ->
            RankingRedisKeys.hourBucket(date.atStartOfDay(RankingRedisKeys.ZONE_ID).plusHours(hour))
        }
        materialize(sourceKeys, RankingRedisKeys.dayBucket(date), ttl)
    }

    fun materializeWeeklyView(
        asOfDate: LocalDate,
        ttl: Duration = Duration.ofDays(40),
    ) {
        val sourceKeys = (0L until 7L).map { offset -> RankingRedisKeys.dayBucket(asOfDate.minusDays(offset)) }
        materialize(sourceKeys, RankingRedisKeys.weeklyView(asOfDate), ttl)
    }

    fun materializeMonthlyView(
        asOfDate: LocalDate,
        ttl: Duration = Duration.ofDays(40),
    ) {
        val sourceKeys = (0L until 30L).map { offset -> RankingRedisKeys.dayBucket(asOfDate.minusDays(offset)) }
        materialize(sourceKeys, RankingRedisKeys.monthlyView(asOfDate), ttl)
    }

    fun getPageFromKey(
        key: String,
        page: Int,
        size: Int,
    ): RankingPage {
        val start = ((page - 1).toLong() * size)
        val end = start + size - 1
        val total = masterRedisTemplate.opsForZSet().size(key) ?: 0L
        val entries = masterRedisTemplate.opsForZSet()
            .reverseRangeWithScores(key, start, end)
            .orEmpty()
            .mapIndexedNotNull { index, tuple ->
                val targetId = tuple.value?.toLongOrNull() ?: return@mapIndexedNotNull null
                RankingScore(targetId = targetId, score = tuple.score ?: 0.0, rank = start + index + 1)
            }
        return RankingPage(entries, total)
    }

    fun getAllFromKey(key: String): List<RankingScore> =
        masterRedisTemplate.opsForZSet()
            .reverseRangeWithScores(key, 0L, -1L)
            .orEmpty()
            .mapIndexedNotNull { index, tuple ->
                val targetId = tuple.value?.toLongOrNull() ?: return@mapIndexedNotNull null
                RankingScore(targetId = targetId, score = tuple.score ?: 0.0, rank = index + 1L)
            }

    fun getPageFromRollingKeys(
        keys: List<String>,
        page: Int,
        size: Int,
    ): RankingPage =
        withMaterializedTemp(keys) { tempKey -> getPageFromKey(tempKey, page, size) }

    fun getTopFromRollingKeys(
        keys: List<String>,
        limit: Int,
    ): List<RankingScore> =
        withMaterializedTemp(keys) { tempKey ->
            masterRedisTemplate.opsForZSet()
                .reverseRangeWithScores(tempKey, 0L, limit.toLong() - 1)
                .orEmpty()
                .mapIndexedNotNull { index, tuple ->
                    val targetId = tuple.value?.toLongOrNull() ?: return@mapIndexedNotNull null
                    RankingScore(targetId = targetId, score = tuple.score ?: 0.0, rank = index + 1L)
                }
        }

    fun findRankFromKey(
        key: String,
        productId: Long,
    ): Long? = masterRedisTemplate.opsForZSet().reverseRank(key, productId.toString())?.plus(1)

    fun findRankFromRollingKeys(
        keys: List<String>,
        productId: Long,
    ): Long? = withMaterializedTemp(keys) { tempKey -> findRankFromKey(tempKey, productId) }

    fun hasKey(key: String): Boolean = masterRedisTemplate.hasKey(key) == true

    private fun materialize(
        sourceKeys: List<String>,
        destinationKey: String,
        ttl: Duration,
    ) {
        if (sourceKeys.isEmpty()) return
        val tempKey = "$destinationKey:tmp:${UUID.randomUUID()}"
        masterRedisTemplate.delete(tempKey)
        masterRedisTemplate.opsForZSet().unionAndStore(sourceKeys.first(), sourceKeys.drop(1), tempKey)
        masterRedisTemplate.expire(tempKey, ttl)
        masterRedisTemplate.rename(tempKey, destinationKey)
    }

    private fun <T> withMaterializedTemp(
        keys: List<String>,
        block: (String) -> T,
    ): T {
        val tempKey = "ranking:${RankingRedisKeys.VERSION}:temp:${UUID.randomUUID()}"
        materialize(keys, tempKey, Duration.ofSeconds(30))
        return try {
            block(tempKey)
        } finally {
            masterRedisTemplate.delete(tempKey)
        }
    }
}
