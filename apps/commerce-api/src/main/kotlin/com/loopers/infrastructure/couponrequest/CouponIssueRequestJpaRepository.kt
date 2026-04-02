package com.loopers.infrastructure.couponrequest

import com.loopers.domain.couponrequest.CouponIssueRequestModel
import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestModel, Long> {
    fun findByRequestId(requestId: String): CouponIssueRequestModel?
}
