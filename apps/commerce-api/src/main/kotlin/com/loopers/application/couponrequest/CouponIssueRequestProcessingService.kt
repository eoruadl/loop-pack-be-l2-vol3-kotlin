package com.loopers.application.couponrequest

import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.couponrequest.CouponIssueRequestStatus
import com.loopers.domain.couponrequest.CouponIssueRequestService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueRequestProcessingService(
    private val couponIssueRequestService: CouponIssueRequestService,
    private val couponTemplateService: CouponTemplateService,
    private val userCouponService: UserCouponService,
) {
    @Transactional
    fun process(requestId: String) {
        val request = couponIssueRequestService.getByRequestId(requestId)
        if (request.status != CouponIssueRequestStatus.REQUESTED) return

        val reserved = couponTemplateService.tryReserveIssue(request.couponTemplateId)
        if (!reserved) {
            couponIssueRequestService.markFailed(
                requestId,
                "쿠폰이 모두 소진되었습니다.",
            )
        }
        if (!reserved) return

        if (userCouponService.tryIssueCoupon(request.userId, request.couponTemplateId)) {
            couponIssueRequestService.markIssued(requestId)
        } else {
            couponTemplateService.releaseIssue(request.couponTemplateId)
            couponIssueRequestService.markFailed(requestId, "이미 발급된 쿠폰입니다.")
        }
    }
}
