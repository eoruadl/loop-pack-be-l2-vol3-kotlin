package com.loopers.messaging.coupon

import java.time.ZonedDateTime

data class CouponIssueRequestMessage(
    val requestId: String,
    val couponTemplateId: Long,
    val userId: Long,
    val occurredAt: ZonedDateTime,
)
