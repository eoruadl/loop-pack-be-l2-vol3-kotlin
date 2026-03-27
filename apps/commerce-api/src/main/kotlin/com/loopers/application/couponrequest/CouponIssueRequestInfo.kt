package com.loopers.application.couponrequest

import com.loopers.domain.couponrequest.CouponIssueRequestModel
import java.time.ZonedDateTime

data class CouponIssueRequestInfo(
    val requestId: String,
    val couponTemplateId: Long,
    val userId: Long,
    val status: String,
    val failureReason: String?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        fun from(model: CouponIssueRequestModel) = CouponIssueRequestInfo(
            requestId = model.requestId,
            couponTemplateId = model.couponTemplateId,
            userId = model.userId,
            status = model.status.name,
            failureReason = model.failureReason,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
        )
    }
}
