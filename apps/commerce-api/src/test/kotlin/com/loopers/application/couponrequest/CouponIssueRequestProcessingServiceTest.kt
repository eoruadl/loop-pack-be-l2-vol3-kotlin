package com.loopers.application.couponrequest

import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.couponrequest.CouponIssueRequestModel
import com.loopers.domain.couponrequest.CouponIssueRequestRepository
import com.loopers.domain.couponrequest.CouponIssueRequestService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CouponIssueRequestProcessingServiceTest {

    private val couponIssueRequestRepository: CouponIssueRequestRepository = mock()
    private val couponIssueRequestService = CouponIssueRequestService(couponIssueRequestRepository)
    private val couponTemplateService: CouponTemplateService = mock()
    private val userCouponService: UserCouponService = mock()
    private val service = CouponIssueRequestProcessingService(couponIssueRequestService, couponTemplateService, userCouponService)

    @Test
    fun `발급 처리 성공 시 요청 상태를 ISSUED로 변경한다`() {
        val request = CouponIssueRequestModel(
            requestId = "req-1",
            userId = 1L,
            couponTemplateId = 10L,
        )
        whenever(couponIssueRequestRepository.findByRequestId("req-1")).thenReturn(request)
        whenever(couponIssueRequestRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(couponTemplateService.tryReserveIssue(10L)).thenReturn(true)
        whenever(userCouponService.tryIssueCoupon(1L, 10L)).thenReturn(true)

        service.process("req-1")

        assertThat(request.status.name).isEqualTo("ISSUED")
        verify(couponTemplateService).tryReserveIssue(10L)
        verify(userCouponService).tryIssueCoupon(1L, 10L)
    }

    @Test
    fun `발급 처리 실패 시 요청 상태를 FAILED로 변경한다`() {
        val request = CouponIssueRequestModel(
            requestId = "req-2",
            userId = 1L,
            couponTemplateId = 10L,
        )
        whenever(couponIssueRequestRepository.findByRequestId("req-2")).thenReturn(request)
        whenever(couponIssueRequestRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(couponTemplateService.tryReserveIssue(10L)).thenReturn(true)
        whenever(userCouponService.tryIssueCoupon(1L, 10L)).thenReturn(false)

        service.process("req-2")

        assertThat(request.status.name).isEqualTo("FAILED")
        assertThat(request.failureReason).isEqualTo("이미 발급된 쿠폰입니다.")
        verify(couponTemplateService).tryReserveIssue(10L)
        verify(couponTemplateService).releaseIssue(10L)
    }

    @Test
    fun `잔여 수량이 없으면 요청 상태를 FAILED로 변경한다`() {
        val request = CouponIssueRequestModel(
            requestId = "req-3",
            userId = 1L,
            couponTemplateId = 10L,
        )
        whenever(couponIssueRequestRepository.findByRequestId("req-3")).thenReturn(request)
        whenever(couponIssueRequestRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(couponTemplateService.tryReserveIssue(10L)).thenReturn(false)

        service.process("req-3")

        assertThat(request.status.name).isEqualTo("FAILED")
        assertThat(request.failureReason).isEqualTo("쿠폰이 모두 소진되었습니다.")
        verify(userCouponService, never()).tryIssueCoupon(any(), any())
    }

    @Test
    fun `이미 처리된 요청이면 발급을 다시 시도하지 않는다`() {
        val request = CouponIssueRequestModel(
            requestId = "req-4",
            userId = 1L,
            couponTemplateId = 10L,
        ).apply { markIssued() }
        whenever(couponIssueRequestRepository.findByRequestId("req-4")).thenReturn(request)

        service.process("req-4")

        verify(userCouponService, never()).tryIssueCoupon(any(), any())
        verify(couponTemplateService, never()).tryReserveIssue(any())
    }
}
