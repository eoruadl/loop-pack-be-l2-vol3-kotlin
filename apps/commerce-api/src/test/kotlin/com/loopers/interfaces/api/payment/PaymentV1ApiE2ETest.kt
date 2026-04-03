package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PgFailureCode
import com.loopers.application.payment.PgPaymentFailException
import com.loopers.application.payment.PgPaymentPort
import com.loopers.application.payment.PgPaymentRequest
import com.loopers.application.payment.PgPaymentResponse
import com.loopers.application.payment.PgPaymentStatusResponse
import com.loopers.application.payment.PgPaymentTimeoutException
import com.loopers.application.product.ProductFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.queue.OrderQueueTokenService
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.task.scheduling.enabled=false"],
)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandService: BrandService,
    private val productFacade: ProductFacade,
    private val orderQueueTokenService: OrderQueueTokenService,
    private val userJpaRepository: UserJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val fakePgPaymentClient: FakePgPaymentClient,
) {
    companion object {
        private const val PAYMENTS = "/api/v1/payments"
        private const val ORDERS = "/api/v1/orders"
        private const val TEST_PASSWORD = "Password123!"
    }

    @TestConfiguration
    class FakePgConfig {
        @Bean
        @Primary
        fun fakePgPaymentClient(): FakePgPaymentClient = FakePgPaymentClient()
    }

    class FakePgPaymentClient : PgPaymentPort {
        var shouldFailRequest = false
        var shouldTimeout = false
        var pgStatus = "SUCCESS"

        override fun requestPayment(request: PgPaymentRequest): PgPaymentResponse {
            if (shouldTimeout) throw PgPaymentTimeoutException("fake timeout")
            if (shouldFailRequest) throw PgPaymentFailException("fake failure")
            return PgPaymentResponse(pgTransactionId = "fake-pg-tx-${request.orderId}")
        }

        override fun getPayment(pgTxId: String, userId: Long): PgPaymentStatusResponse {
            return PgPaymentStatusResponse(
                pgTransactionId = pgTxId,
                status = pgStatus,
                failureCode = if (pgStatus == "FAILED") PgFailureCode.UNKNOWN else null,
            )
        }

        override fun getPaymentByOrderId(orderId: Long, userId: Long): PgPaymentStatusResponse? {
            return PgPaymentStatusResponse(
                pgTransactionId = "fake-pg-tx-ORDER-$orderId",
                status = pgStatus,
                failureCode = if (pgStatus == "FAILED") PgFailureCode.UNKNOWN else null,
            )
        }

        fun reset() {
            shouldFailRequest = false
            shouldTimeout = false
            pgStatus = "SUCCESS"
        }
    }

    @AfterEach
    fun tearDown() {
        fakePgPaymentClient.reset()
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun createUser(loginId: String = "testuser") = userJpaRepository.save(
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = passwordEncryptor.encrypt(TEST_PASSWORD),
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("$loginId@example.com"),
        ),
    )

    private fun createBrand() = brandService.createBrand(
        name = "Nike",
        logoImageUrl = "logo.png",
        description = "테스트 브랜드",
        zipCode = "12345",
        roadAddress = "서울특별시 중구 테스트길 1",
        detailAddress = "1층",
        email = "nike@google.com",
        phoneNumber = "02-3783-4401",
        businessNumber = "123-45-67890",
    )

    private fun createProduct(brandId: Long) = productFacade.createProduct(
        brandId = brandId,
        name = "Air Max",
        imageUrl = "image.png",
        description = "설명",
        price = 50_000L,
        quantity = 10L,
    )

    private fun authHeaders(loginId: String = "testuser") = HttpHeaders().apply {
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", TEST_PASSWORD)
    }

    private fun grantAdmissionToken(loginId: String = "testuser"): String {
        val user = userJpaRepository.findByLoginId(LoginId(loginId))
            ?: error("user not found for loginId=$loginId")
        return orderQueueTokenService.issueToken(user.id).token
    }

    private fun authHeadersWithQueueToken(loginId: String = "testuser", queueToken: String) = authHeaders(loginId).apply {
        set("X-Queue-Token", queueToken)
    }

    private fun createOrderWithPayment(productId: Long): OrderV1Dto.OrderResponse? {
        val queueToken = grantAdmissionToken()
        val request = OrderV1Dto.CreateOrderRequest(
            items = listOf(OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId = productId, quantity = 1L)),
            cardType = CardType.SAMSUNG.name,
            cardNo = "1234-5678-9012-3456",
        )
        val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
        return testRestTemplate.exchange(
            ORDERS,
            HttpMethod.POST,
            HttpEntity(request, authHeadersWithQueueToken(queueToken = queueToken)),
            responseType,
        ).body?.data
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    inner class HandleCallback {

        @Test
        @DisplayName("COMPLETED 콜백 수신 시 Payment COMPLETED, Order PAID로 전환된다")
        fun handleCallback_whenCompleted_thenPaymentCompletedAndOrderPaid() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)

            // 주문+결제 생성 (PG 응답 성공, 콜백 미수신 → PENDING 유지)
            createOrderWithPayment(product.id)

            val payments = paymentJpaRepository.findAll()
            val pgTxId = payments[0].pgTxId!!.value

            // 콜백 수신
            val callbackRequest = PaymentV1Dto.PgCallbackRequest(
                transactionKey = pgTxId,
                orderId = "ORDER-${payments[0].orderId}",
                cardType = CardType.SAMSUNG.name,
                cardNo = "1234-5678-9012-3456",
                amount = 50_000L,
                status = "SUCCESS",
                reason = "정상 승인되었습니다.",
            )
            val callbackResponseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            val callbackResponse = testRestTemplate.exchange(
                "$PAYMENTS/callback",
                HttpMethod.POST,
                HttpEntity(callbackRequest),
                callbackResponseType,
            )

            assertThat(callbackResponse.statusCode).isEqualTo(HttpStatus.OK)

            val updatedPayment = paymentJpaRepository.findById(payments[0].id).get()
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.COMPLETED)
        }
    }

    @DisplayName("POST /api/v1/payments/{paymentId}/recover")
    @Nested
    inner class RecoverPayment {

        @Test
        @DisplayName("PENDING 결제 복구 요청 시 PG 상태 조회 후 동기화된다")
        fun recoverPayment_whenPending_thenSynchronized() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)

            // 주문+결제 생성 (PG 응답 성공, 콜백 미수신 → PENDING 유지)
            createOrderWithPayment(product.id)

            val payments = paymentJpaRepository.findAll()
            assertThat(payments[0].status).isEqualTo(PaymentStatus.PENDING)

            // 수동 복구
            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                "$PAYMENTS/${payments[0].id}/recover",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

            val recovered = paymentJpaRepository.findById(payments[0].id).get()
            assertThat(recovered.status).isEqualTo(PaymentStatus.COMPLETED)
        }
    }
}
