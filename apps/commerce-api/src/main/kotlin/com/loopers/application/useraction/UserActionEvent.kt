package com.loopers.application.useraction

import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import java.time.ZonedDateTime
import java.util.UUID

data class UserActionEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val actionType: UserActionType,
    val actorLoginId: String? = null,
    val targetType: UserActionTargetType? = null,
    val targetId: Long? = null,
    val description: String? = null,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
