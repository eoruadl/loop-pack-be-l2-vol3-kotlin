package com.loopers.domain.useraction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "tb_user_action_log")
class UserActionLogModel(
    eventId: String,
    actionType: UserActionType,
    actorLoginId: String?,
    targetType: UserActionTargetType?,
    targetId: Long?,
    description: String?,
    occurredAt: ZonedDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: String = eventId
        protected set

    @Column(name = "action_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var actionType: UserActionType = actionType
        protected set

    @Column(name = "actor_login_id")
    var actorLoginId: String? = actorLoginId
        protected set

    @Column(name = "target_type")
    @Enumerated(EnumType.STRING)
    var targetType: UserActionTargetType? = targetType
        protected set

    @Column(name = "target_id")
    var targetId: Long? = targetId
        protected set

    @Column(name = "description")
    var description: String? = description
        protected set

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: ZonedDateTime = occurredAt
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        createdAt = ZonedDateTime.now()
    }
}
