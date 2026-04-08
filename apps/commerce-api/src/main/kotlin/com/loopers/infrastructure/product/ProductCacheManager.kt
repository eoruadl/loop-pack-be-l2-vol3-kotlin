package com.loopers.infrastructure.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.ProductInfo
import com.loopers.config.redis.RedisConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

@Component
class ProductCacheManager(
    private val defaultRedisTemplate: RedisTemplate<String, String>,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val DETAIL_PREFIX = "product:detail:"
        private const val LIST_PREFIX = "product:list:"
        private const val LIKE_COUNT_PREFIX = "product:likecount:"
        private val DETAIL_TTL = Duration.ofMinutes(30)
        private val LIST_TTL_SHORT = Duration.ofMinutes(1)
        private val LIST_TTL_LONG = Duration.ofMinutes(10)
        private val LIKE_COUNT_TTL = Duration.ofMinutes(1)
        const val LIST_MAX_CACHED_PAGE = 5
        private val DETAIL_JITTER = Duration.ofMinutes(1)
        private val LIST_JITTER_LONG = Duration.ofSeconds(30)
        private val LIST_JITTER_SHORT = Duration.ofSeconds(10)
        private val LIKE_COUNT_JITTER = Duration.ofSeconds(10)
    }

    private fun jitter(base: Duration, offset: Duration): Duration {
        val offsetMs = ThreadLocalRandom.current().nextLong(-offset.toMillis(), offset.toMillis() + 1)
        return base.plusMillis(offsetMs)
    }

    fun detailKey(id: Long): String = "$DETAIL_PREFIX$id"

    fun likeCountKey(id: Long): String = "$LIKE_COUNT_PREFIX$id"

    fun listKey(brandId: Long?, sort: Sort, page: Int): String {
        val brandPart = brandId?.toString() ?: "all"
        val order = sort.firstOrNull()
        val sortPart = if (order != null) "${order.property}:${order.direction}" else "unsorted"
        return "$LIST_PREFIX$brandPart:$sortPart:$page"
    }

    fun listTtl(sort: Sort): Duration {
        val order = sort.firstOrNull()
        return if (order != null && order.property == "likeCount" && order.isDescending) {
            LIST_TTL_SHORT
        } else {
            LIST_TTL_LONG
        }
    }

    fun getDetail(id: Long): ProductInfo? {
        val json = defaultRedisTemplate.opsForValue().get(detailKey(id)) ?: return null
        val info = runCatching { objectMapper.readValue(json, ProductInfo::class.java) }.getOrNull() ?: return null
        val cachedLikeCount = defaultRedisTemplate.opsForValue().get(likeCountKey(id))?.toLongOrNull()
        return if (cachedLikeCount != null) info.copy(likeCount = cachedLikeCount) else info
    }

    fun putDetail(info: ProductInfo) {
        val json = objectMapper.writeValueAsString(info)
        masterRedisTemplate.opsForValue().set(detailKey(info.id), json, jitter(DETAIL_TTL, DETAIL_JITTER))
        masterRedisTemplate.opsForValue().set(
            likeCountKey(info.id),
            info.likeCount.toString(),
            jitter(LIKE_COUNT_TTL, LIKE_COUNT_JITTER),
        )
    }

    fun evictDetail(id: Long) {
        masterRedisTemplate.delete(detailKey(id))
        masterRedisTemplate.delete(likeCountKey(id))
    }

    fun getList(brandId: Long?, sort: Sort, page: Int): Page<ProductInfo>? {
        val key = listKey(brandId, sort, page)
        val json = defaultRedisTemplate.opsForValue().get(key) ?: return null
        return runCatching {
            val cached = objectMapper.readValue(json, object : TypeReference<CachedPage<ProductInfo>>() {})
            PageImpl(cached.content, PageRequest.of(cached.page, cached.size, sort), cached.totalElements)
        }.getOrNull()
    }

    fun putList(brandId: Long?, sort: Sort, page: Int, result: Page<ProductInfo>) {
        val key = listKey(brandId, sort, page)
        val cached = CachedPage(
            content = result.content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
        val json = objectMapper.writeValueAsString(cached)
        val jitterOffset = if (sort.firstOrNull()?.let { it.property == "likeCount" && it.isDescending } == true) {
            LIST_JITTER_SHORT
        } else {
            LIST_JITTER_LONG
        }
        masterRedisTemplate.opsForValue().set(key, json, jitter(listTtl(sort), jitterOffset))
    }

    fun evictAllList() {
        val keys = masterRedisTemplate.keys("$LIST_PREFIX*")
        if (!keys.isNullOrEmpty()) {
            masterRedisTemplate.delete(keys)
        }
    }

    private data class CachedPage<T>(
        val content: List<T>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    )
}
