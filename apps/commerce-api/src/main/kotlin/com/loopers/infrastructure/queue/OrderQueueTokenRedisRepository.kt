package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.ActiveQueueToken
import com.loopers.domain.queue.OrderQueueTokenRepository
import com.loopers.domain.queue.TokenConsumeResult
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

@Component
class OrderQueueTokenRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
) : OrderQueueTokenRepository {
    companion object {
        private const val RESULT_SUCCESS = "SUCCESS"
        private const val RESULT_MISSING = "TOKEN_MISSING"
        private const val RESULT_MISMATCH = "TOKEN_MISMATCH"
        private const val RESULT_ALREADY_CONSUMED = "TOKEN_ALREADY_CONSUMED"
        private const val RESULT_TOO_EARLY = "TOKEN_TOO_EARLY"
    }

    private val consumeScript = DefaultRedisScript<String>().apply {
        setScriptText(
            """
            local active = redis.call('GET', KEYS[1])
            if not active then
                if redis.call('EXISTS', KEYS[2]) == 1 then
                    return '$RESULT_ALREADY_CONSUMED'
                end
                return '$RESULT_MISSING'
            end

            local firstSep = string.find(active, '|')
            local secondSep = string.find(active, '|', firstSep + 1)
            if not firstSep or not secondSep then
                return '$RESULT_MISSING'
            end

            local token = string.sub(active, 1, firstSep - 1)
            local usableAt = tonumber(string.sub(active, secondSep + 1))
            local requestedToken = ARGV[1]
            local nowMillis = tonumber(ARGV[2])
            local consumedTtlSeconds = tonumber(ARGV[3])

            if token ~= requestedToken then
                return '$RESULT_MISMATCH'
            end

            if usableAt > nowMillis then
                return '$RESULT_TOO_EARLY:' .. tostring(usableAt - nowMillis)
            end

            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], tostring(nowMillis), 'EX', consumedTtlSeconds)
            return '$RESULT_SUCCESS'
            """.trimIndent(),
        )
        resultType = String::class.java
    }

    override fun issueToken(userId: Long, issuedAt: Instant, usableAt: Instant, ttl: Duration): ActiveQueueToken {
        val token = UUID.randomUUID().toString()
        masterRedisTemplate.opsForValue().set(tokenKey(userId), encode(token, issuedAt, usableAt), ttl)
        return ActiveQueueToken(
            userId = userId,
            token = token,
            issuedAt = issuedAt,
            usableAt = usableAt,
            expiresAt = issuedAt.plus(ttl),
        )
    }

    override fun getActiveToken(userId: Long): ActiveQueueToken? {
        val value = masterRedisTemplate.opsForValue().get(tokenKey(userId)) ?: return null
        val decoded = decode(value) ?: return null
        val ttlMillis = masterRedisTemplate.getExpire(tokenKey(userId), TimeUnit.MILLISECONDS)
        if (ttlMillis <= 0) return null

        return ActiveQueueToken(
            userId = userId,
            token = decoded.token,
            issuedAt = decoded.issuedAt,
            usableAt = decoded.usableAt,
            expiresAt = Instant.now().plusMillis(ttlMillis),
        )
    }

    override fun consumeIfValid(userId: Long, token: String, now: Instant, consumedTtl: Duration): TokenConsumeResult {
        val result = masterRedisTemplate.execute(
            consumeScript,
            listOf(tokenKey(userId), consumedKey(userId)),
            token,
            now.toEpochMilli().toString(),
            max(1L, consumedTtl.seconds).toString(),
        ) ?: RESULT_MISSING

        return when {
            result == RESULT_SUCCESS -> TokenConsumeResult.Success
            result == RESULT_MISSING -> TokenConsumeResult.Missing
            result == RESULT_MISMATCH -> TokenConsumeResult.Mismatch
            result == RESULT_ALREADY_CONSUMED -> TokenConsumeResult.AlreadyConsumed
            result.startsWith("$RESULT_TOO_EARLY:") -> {
                val waitMillis = result.substringAfter(':').toLongOrNull() ?: 0L
                TokenConsumeResult.TooEarly(
                    retryAfterSeconds = ceil(max(waitMillis, 0L).toDouble() / 1000.0).toLong(),
                )
            }

            else -> TokenConsumeResult.Missing
        }
    }

    override fun hasActiveToken(userId: Long): Boolean =
        masterRedisTemplate.hasKey(tokenKey(userId)) == true

    override fun revokeToken(userId: Long): Boolean =
        masterRedisTemplate.delete(tokenKey(userId))

    private fun tokenKey(userId: Long): String = "queue:order:token:$userId"

    private fun consumedKey(userId: Long): String = "queue:order:token-consumed:$userId"

    private fun encode(token: String, issuedAt: Instant, usableAt: Instant): String =
        "$token|${issuedAt.toEpochMilli()}|${usableAt.toEpochMilli()}"

    private fun decode(value: String): DecodedToken? {
        val parts = value.split("|", limit = 3)
        if (parts.size != 3) return null
        val issuedAtMillis = parts[1].toLongOrNull() ?: return null
        val usableAtMillis = parts[2].toLongOrNull() ?: return null
        return DecodedToken(
            token = parts[0],
            issuedAt = Instant.ofEpochMilli(issuedAtMillis),
            usableAt = Instant.ofEpochMilli(usableAtMillis),
        )
    }

    private data class DecodedToken(
        val token: String,
        val issuedAt: Instant,
        val usableAt: Instant,
    )
}
