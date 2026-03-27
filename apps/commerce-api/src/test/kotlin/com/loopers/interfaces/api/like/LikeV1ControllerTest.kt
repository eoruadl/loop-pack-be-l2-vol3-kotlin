package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.application.like.LikeInfo
import com.loopers.application.useraction.UserActionEvent
import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import com.loopers.interfaces.api.auth.AuthenticatedUser
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable

@ExtendWith(MockKExtension::class)
class LikeV1ControllerTest {

    private val likeFacade: LikeFacade = mockk()
    private val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val controller = LikeV1Controller(likeFacade, applicationEventPublisher)

    @Test
    fun `좋아요 요청 시 유저 액션 이벤트를 발행한다`() {
        every { likeFacade.like("testuser", 1L) } returns LikeInfo(id = 1L, userId = 1L, productId = 1L)

        controller.like(1L, AuthenticatedUser("testuser", "1990-01-01"))

        verify {
            applicationEventPublisher.publishEvent(
                match<UserActionEvent> {
                    it.actionType == UserActionType.PRODUCT_LIKE &&
                        it.actorLoginId == "testuser" &&
                        it.targetType == UserActionTargetType.PRODUCT &&
                        it.targetId == 1L
                }
            )
        }
    }

    @Test
    fun `좋아요 취소 요청 시 유저 액션 이벤트를 발행한다`() {
        every { likeFacade.unlike("testuser", 1L) } returns Unit

        controller.unlike(1L, AuthenticatedUser("testuser", "1990-01-01"))

        verify {
            applicationEventPublisher.publishEvent(
                match<UserActionEvent> {
                    it.actionType == UserActionType.PRODUCT_UNLIKE &&
                        it.actorLoginId == "testuser" &&
                        it.targetType == UserActionTargetType.PRODUCT &&
                        it.targetId == 1L
                }
            )
        }
    }

    @Test
    fun `좋아요 목록 조회는 기존 동작을 유지한다`() {
        every { likeFacade.getLikedProducts("testuser", 1L, any<Pageable>()) } returns emptyList()

        controller.getLikedProducts(1L, AuthenticatedUser("testuser", "1990-01-01"), Pageable.unpaged())

        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<UserActionEvent>()) }
    }
}
