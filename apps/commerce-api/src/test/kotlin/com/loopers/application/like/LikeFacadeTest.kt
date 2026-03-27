package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.like.LikeModel
import com.loopers.domain.user.UserService
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockitoExtension::class)
class LikeFacadeTest {

    @Mock
    private lateinit var likeService: LikeService

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @InjectMocks
    private lateinit var likeFacade: LikeFacade

    private fun createTestUserModel(loginId: String = "testuser"): UserModel =
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = "encrypted",
            name = com.loopers.domain.user.Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("test@example.com"),
        )

    private fun createTestLikeModel(userId: Long = 1L, productId: Long = 1L): LikeModel =
        LikeModel(userId = userId, productId = productId)

    @Nested
    inner class Like {

        @Test
        fun `like() 호출 시 LikeInfo를 반환한다`() {
            val userModel = createTestUserModel()
            val likeModel = createTestLikeModel()
            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)
            whenever(likeService.like(any(), any())).thenReturn(Pair(true, likeModel))

            val result = likeFacade.like("testuser", 1L)

            assertThat(result).isInstanceOf(LikeInfo::class.java)
            assertThat(result.userId).isEqualTo(likeModel.userId)
            assertThat(result.productId).isEqualTo(likeModel.productId)
            verify(applicationEventPublisher).publishEvent(
                argThat<LikeCountChangedEvent> {
                    productId == 1L && type == LikeCountChangedEvent.Type.INCREASE
                }
            )
        }

        @Test
        fun `이미 좋아요가 존재하면 집계 이벤트를 발행하지 않는다`() {
            val userModel = createTestUserModel()
            val likeModel = createTestLikeModel()
            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)
            whenever(likeService.like(any(), any())).thenReturn(Pair(false, likeModel))

            likeFacade.like("testuser", 1L)

            verify(applicationEventPublisher, never()).publishEvent(any<LikeCountChangedEvent>())
        }
    }

    @Nested
    inner class Unlike {

        @Test
        fun `unlike() 호출 시 정상적으로 완료된다`() {
            val userModel = createTestUserModel()
            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)
            whenever(likeService.unlike(any(), any())).thenReturn(true)

            val result = likeFacade.unlike("testuser", 1L)

            assertThat(result).isEqualTo(Unit)
            verify(applicationEventPublisher).publishEvent(
                argThat<LikeCountChangedEvent> {
                    productId == 1L && type == LikeCountChangedEvent.Type.DECREASE
                }
            )
        }

        @Test
        fun `unlike 대상이 없으면 집계 이벤트를 발행하지 않는다`() {
            val userModel = createTestUserModel()
            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)
            whenever(likeService.unlike(any(), any())).thenReturn(false)

            likeFacade.unlike("testuser", 1L)

            verify(applicationEventPublisher, never()).publishEvent(any<LikeCountChangedEvent>())
        }
    }

    @Nested
    inner class GetLikedProducts {

        @Test
        fun `본인이 좋아요한 상품 목록 조회 시 LikeInfo 목록을 반환한다`() {
            // given
            val userModel = createTestUserModel()  // user.id = 0 (기본값)
            val likeModels = listOf(
                createTestLikeModel(userId = 0L, productId = 1L),
                createTestLikeModel(userId = 0L, productId = 2L),
            )
            val pageable = PageRequest.of(0, 10)

            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)
            whenever(likeService.getLikedProducts(0L, pageable)).thenReturn(likeModels)

            // when
            val result = likeFacade.getLikedProducts("testuser", 0L, pageable)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0]).isInstanceOf(LikeInfo::class.java)
            assertThat(result[0].productId).isEqualTo(1L)
            assertThat(result[1].productId).isEqualTo(2L)
        }

        @Test
        fun `타인의 좋아요 목록 조회 시 FORBIDDEN 예외가 발생한다`() {
            // given
            val userModel = createTestUserModel()  // user.id = 0 (기본값)

            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)

            // when
            val exception = assertThrows<CoreException> {
                likeFacade.getLikedProducts("testuser", 999L, PageRequest.of(0, 10))
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.FORBIDDEN)
        }
    }
}
