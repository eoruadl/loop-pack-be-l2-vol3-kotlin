package com.loopers.domain.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "tb_product_metrics")
class ProductMetricsModel(
    productId: Long,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long = productId
        protected set

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0L
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0L
        protected set

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0L
        protected set

    @Column(name = "last_event_at")
    var lastEventAt: ZonedDateTime? = null
        protected set

    fun increaseViewCount(occurredAt: ZonedDateTime) {
        viewCount += 1
        touch(occurredAt)
    }

    fun increaseLikeCount(occurredAt: ZonedDateTime) {
        likeCount += 1
        touch(occurredAt)
    }

    fun decreaseLikeCount(occurredAt: ZonedDateTime) {
        likeCount = (likeCount - 1).coerceAtLeast(0)
        touch(occurredAt)
    }

    fun increaseSalesCount(quantity: Long, occurredAt: ZonedDateTime) {
        salesCount += quantity
        touch(occurredAt)
    }

    private fun touch(occurredAt: ZonedDateTime) {
        lastEventAt = maxOf(lastEventAt ?: occurredAt, occurredAt)
    }
}
