package com.loopers.application.like

import java.time.ZonedDateTime

data class LikeCountChangedEvent(
    val productId: Long,
    val type: Type,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
) {
    enum class Type {
        INCREASE,
        DECREASE,
    }
}
