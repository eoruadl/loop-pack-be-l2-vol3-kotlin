package com.loopers.domain.couponrequest

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CouponIssueRequestService(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
) {
    @Transactional
    fun create(userId: Long, couponTemplateId: Long): CouponIssueRequestModel =
        couponIssueRequestRepository.save(
            CouponIssueRequestModel(
                requestId = UUID.randomUUID().toString(),
                userId = userId,
                couponTemplateId = couponTemplateId,
            ),
        )

    @Transactional(readOnly = true)
    fun getByRequestId(requestId: String): CouponIssueRequestModel =
        couponIssueRequestRepository.findByRequestId(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 요청을 찾을 수 없습니다.")

    @Transactional
    fun markIssued(requestId: String): CouponIssueRequestModel {
        val request = getByRequestId(requestId)
        request.markIssued()
        return couponIssueRequestRepository.save(request)
    }

    @Transactional
    fun markFailed(requestId: String, reason: String): CouponIssueRequestModel {
        val request = getByRequestId(requestId)
        request.markFailed(reason)
        return couponIssueRequestRepository.save(request)
    }
}
