package com.loopers.infrastructure.couponrequest

import com.loopers.domain.couponrequest.CouponIssueRequestModel
import com.loopers.domain.couponrequest.CouponIssueRequestRepository
import org.springframework.stereotype.Repository

@Repository
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {
    override fun save(model: CouponIssueRequestModel): CouponIssueRequestModel =
        couponIssueRequestJpaRepository.save(model)

    override fun findByRequestId(requestId: String): CouponIssueRequestModel? =
        couponIssueRequestJpaRepository.findByRequestId(requestId)
}
