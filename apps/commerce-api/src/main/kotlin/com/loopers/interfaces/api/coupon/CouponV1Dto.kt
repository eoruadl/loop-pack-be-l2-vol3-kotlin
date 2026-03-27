package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.UserCouponInfo
import java.time.ZonedDateTime

class CouponV1Dto {

    data class UserCouponResponse(
        val id: Long,
        val couponTemplateId: Long,
        val status: String,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: UserCouponInfo) = UserCouponResponse(
                id = info.id,
                couponTemplateId = info.couponTemplateId,
                status = info.status,
                createdAt = info.createdAt,
                updatedAt = info.updatedAt,
            )
        }
    }

    data class CouponIssueRequestResponse(
        val requestId: String,
        val couponTemplateId: Long,
        val userId: Long,
        val status: String,
        val failureReason: String?,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: com.loopers.application.couponrequest.CouponIssueRequestInfo) = CouponIssueRequestResponse(
                requestId = info.requestId,
                couponTemplateId = info.couponTemplateId,
                userId = info.userId,
                status = info.status,
                failureReason = info.failureReason,
                createdAt = info.createdAt,
                updatedAt = info.updatedAt,
            )
        }
    }
}
