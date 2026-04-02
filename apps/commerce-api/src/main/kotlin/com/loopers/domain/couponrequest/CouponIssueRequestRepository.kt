package com.loopers.domain.couponrequest

interface CouponIssueRequestRepository {
    fun save(model: CouponIssueRequestModel): CouponIssueRequestModel
    fun findByRequestId(requestId: String): CouponIssueRequestModel?
}
