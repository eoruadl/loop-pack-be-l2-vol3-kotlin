package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.couponrequest.CouponIssueRequestFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponV1Controller(
    private val couponFacade: CouponFacade,
    private val couponIssueRequestFacade: CouponIssueRequestFacade,
) : CouponV1ApiSpec {

    @PostMapping("/{couponId}/issue")
    override fun issueCoupon(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.UserCouponResponse> =
        couponFacade.issueCoupon(authenticatedUser.loginId, couponId)
            .let { CouponV1Dto.UserCouponResponse.from(it) }
            .let { ApiResponse.success(it) }

    @PostMapping("/{couponId}/issue-requests")
    override fun requestCouponIssue(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponIssueRequestResponse> =
        couponIssueRequestFacade.requestIssue(authenticatedUser.loginId, couponId)
            .let { CouponV1Dto.CouponIssueRequestResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/issue-requests/{requestId}")
    override fun getCouponIssueRequest(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @PathVariable requestId: String,
    ): ApiResponse<CouponV1Dto.CouponIssueRequestResponse> =
        couponIssueRequestFacade.getRequest(authenticatedUser.loginId, requestId)
            .let { CouponV1Dto.CouponIssueRequestResponse.from(it) }
            .let { ApiResponse.success(it) }
}
