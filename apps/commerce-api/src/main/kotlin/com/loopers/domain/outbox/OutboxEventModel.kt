package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "tb_outbox_event")
class OutboxEventModel(
    eventId: String,
    topic: String,
    partition: Int? = null,
    eventKey: String,
    eventType: String,
    payload: String,
    status: OutboxEventStatus = OutboxEventStatus.PENDING,
) : BaseEntity() {
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: String = eventId
        protected set

    @Column(name = "topic_name", nullable = false)
    var topic: String = topic
        protected set

    @Column(name = "partition_no")
    var partition: Int? = partition
        protected set

    @Column(name = "event_key", nullable = false)
    var eventKey: String = eventKey
        protected set

    @Column(name = "event_type", nullable = false)
    var eventType: String = eventType
        protected set

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    var payload: String = payload
        protected set

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: OutboxEventStatus = status
        protected set

    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null
        protected set

    @Column(name = "failed_reason")
    var failedReason: String? = null
        protected set

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0
        protected set

    fun markPublished() {
        status = OutboxEventStatus.PUBLISHED
        publishedAt = ZonedDateTime.now()
        failedReason = null
        attemptCount += 1
    }

    fun markFailed(reason: String) {
        status = OutboxEventStatus.FAILED
        failedReason = reason
        attemptCount += 1
    }
}
