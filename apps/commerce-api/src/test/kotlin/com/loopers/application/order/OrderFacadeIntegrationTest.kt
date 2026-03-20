package com.loopers.application.order

import com.loopers.application.payment.PgPaymentPort
import com.loopers.application.payment.PgPaymentRequest
import com.loopers.application.payment.PgPaymentResponse
import com.loopers.application.payment.PgPaymentStatusResponse
import com.loopers.application.product.ProductFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.payment.CardType
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val brandService: BrandService,
    private val productFacade: ProductFacade,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @TestConfiguration
    class FakePgConfig {
        @Bean
        @Primary
        fun fakePgPaymentClient(): PgPaymentPort = object : PgPaymentPort {
            override fun requestPayment(request: PgPaymentRequest): CompletableFuture<PgPaymentResponse> =
                CompletableFuture.completedFuture(PgPaymentResponse(pgTransactionId = "fake-pg-tx-${request.orderId}"))

            override fun getPayment(pgTxId: String, userId: Long): CompletableFuture<PgPaymentStatusResponse> =
                CompletableFuture.completedFuture(
                    PgPaymentStatusResponse(pgTransactionId = pgTxId, status = "SUCCESS", failureCode = null)
                )

            override fun getPaymentByOrderId(orderId: Long, userId: Long): CompletableFuture<PgPaymentStatusResponse?> =
                CompletableFuture.completedFuture(
                    PgPaymentStatusResponse(pgTransactionId = "fake-pg-tx-ORDER-$orderId", status = "SUCCESS", failureCode = null)
                )
        }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val testPassword = "Password123!"
    private val defaultCardType = CardType.SAMSUNG
    private val defaultCardNo = "1234567890123456"

    private fun createUser(loginId: String = "testuser") = userJpaRepository.save(
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = passwordEncryptor.encrypt(testPassword),
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("$loginId@example.com"),
        )
    )

    private fun createBrand() = brandService.createBrand(
        name = "Nike",
        logoImageUrl = "test.png",
        description = "테스트 브랜드",
        zipCode = "12345",
        roadAddress = "서울특별시 중구 테스트길 1",
        detailAddress = "1층",
        email = "nike@google.com",
        phoneNumber = "02-3783-4401",
        businessNumber = "123-45-67890",
    )

    private fun createProduct(brandId: Long, quantity: Long = 10L) = productFacade.createProduct(
        brandId = brandId,
        name = "Air Max",
        imageUrl = "image.png",
        description = "설명",
        price = 50_000L,
        quantity = quantity,
    )

    @Nested
    inner class CreateOrder {

        @Test
        fun `사용자 상품 재고 생성 후 주문 시 OrderWithPaymentInfo와 items를 반환한다`() {
            val user = createUser()
            val brand = createBrand()
            val product = createProduct(brand.id, quantity = 10L)

            val result = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 2L)),
                couponId = null,
                cardType = defaultCardType,
                cardNo = defaultCardNo,
            )

            assertThat(result.order.userId).isEqualTo(user.id)
            assertThat(result.order.totalAmount).isEqualTo(100_000L)
            assertThat(result.order.items).hasSize(1)
            assertThat(result.order.items[0].productId).isEqualTo(product.id)
            assertThat(result.order.items[0].quantity).isEqualTo(2L)
            assertThat(result.order.items[0].subTotal).isEqualTo(100_000L)
            assertThat(result.paymentId).isGreaterThan(0L)
        }

        @Test
        fun `재고 부족 시 BAD_REQUEST 예외가 발생한다`() {
            createUser()
            val brand = createBrand()
            createProduct(brand.id, quantity = 1L)
            val productInfo = productFacade.getProducts(brand.id, PageRequest.of(0, 1)).content.first()

            val exception = assertThrows<CoreException> {
                orderFacade.createOrder(
                    loginId = "testuser",
                    items = listOf(OrderFacade.OrderItemRequest(productId = productInfo.id, quantity = 5L)),
                    couponId = null,
                    cardType = defaultCardType,
                    cardNo = defaultCardNo,
                )
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Nested
    inner class GetOrders {

        @Test
        fun `날짜 범위 주문 목록 조회 시 해당 사용자의 주문만 반환한다`() {
            val user = createUser()
            val brand = createBrand()
            val product = createProduct(brand.id, quantity = 100L)

            orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
                cardType = defaultCardType,
                cardNo = defaultCardNo,
            )
            orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
                cardType = defaultCardType,
                cardNo = defaultCardNo,
            )

            val result = orderFacade.getOrders(
                loginId = "testuser",
                startAt = LocalDate.now().minusDays(1),
                endAt = LocalDate.now().plusDays(1),
                pageable = PageRequest.of(0, 10),
            )

            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.userId }).containsOnly(user.id)
        }
    }

    @Nested
    inner class GetOrderById {

        @Test
        fun `본인 주문 조회 시 OrderInfo를 반환한다`() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)

            val created = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
                cardType = defaultCardType,
                cardNo = defaultCardNo,
            )

            val result = orderFacade.getOrderById("testuser", created.order.id)

            assertThat(result.id).isEqualTo(created.order.id)
            assertThat(result.items).hasSize(1)
        }

        @Test
        fun `타인 주문 조회 시 FORBIDDEN 예외가 발생한다`() {
            createUser("testuser")
            createUser("otheruser")
            val brand = createBrand()
            val product = createProduct(brand.id)

            val otherOrder = orderFacade.createOrder(
                loginId = "otheruser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
                cardType = defaultCardType,
                cardNo = defaultCardNo,
            )

            val exception = assertThrows<CoreException> {
                orderFacade.getOrderById("testuser", otherOrder.order.id)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.FORBIDDEN)
        }
    }

    @Nested
    inner class GetAllOrders {

        @Test
        fun `관리자 전체 주문 목록 조회 시 페이징으로 반환한다`() {
            createUser("user1")
            createUser("user2")
            val brand = createBrand()
            val product = createProduct(brand.id, quantity = 100L)

            orderFacade.createOrder(
                loginId = "user1",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
                cardType = defaultCardType,
                cardNo = defaultCardNo,
            )
            orderFacade.createOrder(
                loginId = "user2",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
                cardType = defaultCardType,
                cardNo = defaultCardNo,
            )

            val result = orderFacade.getAllOrders(PageRequest.of(0, 10))

            assertThat(result.content).hasSize(2)
            assertThat(result.totalElements).isEqualTo(2L)
        }
    }
}
