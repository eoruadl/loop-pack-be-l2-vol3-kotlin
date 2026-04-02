package com.loopers.application.couponrequest

import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.couponrequest.CouponIssueRequestService
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponIssueRequestFacade(
    private val couponIssueRequestService: CouponIssueRequestService,
    private val couponIssueRequestOutboxService: CouponIssueRequestOutboxService,
    private val couponTemplateService: CouponTemplateService,
    private val userService: UserService,
) {
    @Transactional
    fun requestIssue(loginId: String, couponTemplateId: Long): CouponIssueRequestInfo {
        val user = userService.getUserByLoginId(loginId)
        couponTemplateService.getTemplateById(couponTemplateId)

        val request = couponIssueRequestService.create(user.id, couponTemplateId)
        val info = CouponIssueRequestInfo.from(request)
        couponIssueRequestOutboxService.enqueue(info)
        return info
    }

    @Transactional(readOnly = true)
    fun getRequest(loginId: String, requestId: String): CouponIssueRequestInfo {
        val user = userService.getUserByLoginId(loginId)
        val request = couponIssueRequestService.getByRequestId(requestId)
        if (request.userId != user.id) {
            throw com.loopers.support.error.CoreException(
                com.loopers.support.error.ErrorType.FORBIDDEN,
                "본인의 쿠폰 발급 요청만 조회할 수 있습니다.",
            )
        }
        return CouponIssueRequestInfo.from(request)
    }
}
