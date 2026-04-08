package com.loopers.application.couponrequest

import com.loopers.domain.coupon.CouponTemplateModel
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.CouponValue
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.couponrequest.CouponIssueRequestModel
import com.loopers.domain.couponrequest.CouponIssueRequestRepository
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.ZonedDateTime

class CouponIssueRequestFacadeTest {

    private val couponIssueRequestRepository: CouponIssueRequestRepository = mock()
    private val couponIssueRequestService = com.loopers.domain.couponrequest.CouponIssueRequestService(couponIssueRequestRepository)
    private val couponIssueRequestOutboxService: CouponIssueRequestOutboxService = mock()
    private val couponTemplateService: CouponTemplateService = mock()
    private val userService: UserService = mock()
    private val facade = CouponIssueRequestFacade(
        couponIssueRequestService = couponIssueRequestService,
        couponIssueRequestOutboxService = couponIssueRequestOutboxService,
        couponTemplateService = couponTemplateService,
        userService = userService,
    )

    @Test
    fun `쿠폰 발급 요청을 생성하고 아웃박스에 적재한다`() {
        val user = UserModel(
            loginId = LoginId("testuser"),
            encryptedPassword = "encrypted",
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("test@example.com"),
        )
        val requestModel = CouponIssueRequestModel(
            requestId = "req-1",
            userId = user.id,
            couponTemplateId = 1L,
        )
        val now = ZonedDateTime.now()
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = CouponIssueRequestModel::class.java.superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(requestModel, now)
        }

        whenever(userService.getUserByLoginId("testuser")).thenReturn(user)
        whenever(couponTemplateService.getTemplateById(1L)).thenReturn(
            CouponTemplateModel(
                name = CouponName("테스트쿠폰"),
                type = CouponType.FIXED,
                value = CouponValue(1000L),
                minOrderAmount = null,
                expiredAt = ZonedDateTime.now().plusDays(1),
            ),
        )
        whenever(couponIssueRequestRepository.save(any())).thenReturn(requestModel)

        val result = facade.requestIssue("testuser", 1L)

        assertThat(result.requestId).isEqualTo("req-1")
        assertThat(result.status).isEqualTo("REQUESTED")
        verify(couponIssueRequestOutboxService).enqueue(any())
    }

    @Test
    fun `본인 요청만 조회할 수 있다`() {
        val other = UserModel(
            loginId = LoginId("other"),
            encryptedPassword = "encrypted",
            name = Name("김철수"),
            birthDate = BirthDate("1991-01-01"),
            email = Email("other@example.com"),
        )
        val requestModel = CouponIssueRequestModel(
            requestId = "req-2",
            userId = 999L,
            couponTemplateId = 1L,
        )

        whenever(userService.getUserByLoginId("other")).thenReturn(other)
        whenever(couponIssueRequestRepository.findByRequestId("req-2")).thenReturn(requestModel)

        assertThrows<CoreException> {
            facade.getRequest("other", "req-2")
        }
    }
}
