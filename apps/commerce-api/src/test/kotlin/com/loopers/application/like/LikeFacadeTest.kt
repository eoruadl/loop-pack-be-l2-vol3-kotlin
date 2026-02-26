package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.like.LikeModel
import com.loopers.domain.product.Description
import com.loopers.domain.product.ImageUrl
import com.loopers.domain.product.Name
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class LikeFacadeTest {

    @Mock
    private lateinit var likeService: LikeService

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var productService: ProductService

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

    private fun createTestProductModel(): ProductModel =
        ProductModel(1L, Name("뉴발란스 991"), ImageUrl("test.png"), Description("신발"), Price(299_000L))

    private fun createTestLikeModel(userId: Long = 1L, productId: Long = 1L): LikeModel =
        LikeModel(userId = userId, productId = productId)

    @Nested
    inner class Like {

        @Test
        fun `like() 호출 시 LikeInfo를 반환한다`() {
            val userModel = createTestUserModel()
            val productModel = createTestProductModel()
            val likeModel = createTestLikeModel()
            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)
            whenever(productService.getProductById(any())).thenReturn(productModel)
            whenever(likeService.like(any(), any())).thenReturn(likeModel)

            val result = likeFacade.like("testuser", 1L)

            assertThat(result).isInstanceOf(LikeInfo::class.java)
            assertThat(result.userId).isEqualTo(likeModel.userId)
            assertThat(result.productId).isEqualTo(likeModel.productId)
        }
    }

    @Nested
    inner class Unlike {

        @Test
        fun `unlike() 호출 시 정상적으로 완료된다`() {
            val userModel = createTestUserModel()
            val productModel = createTestProductModel()
            whenever(userService.getUserByLoginId(any())).thenReturn(userModel)
            whenever(productService.getProductById(any())).thenReturn(productModel)

            val result = likeFacade.unlike("testuser", 1L)

            assertThat(result).isEqualTo(Unit)
        }
    }
}
