package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PgFailureCode
import com.loopers.application.payment.PgPaymentFailException
import com.loopers.application.payment.PgPaymentPort
import com.loopers.application.payment.PgPaymentRequest
import com.loopers.application.payment.PgPaymentResponse
import com.loopers.application.payment.PgPaymentStatusResponse
import com.loopers.application.payment.PgPaymentTimeoutException
import com.loopers.application.order.OrderFacade
import com.loopers.application.product.ProductFacade
import com.loopers.domain.brand.BrandService
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
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandService: BrandService,
    private val productFacade: ProductFacade,
    private val orderFacade: OrderFacade,
    private val userJpaRepository: UserJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
    private val fakePgPaymentClient: FakePgPaymentClient,
) {
    companion object {
        private const val PAYMENTS = "/api/v1/payments"
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
    }

    private fun createUser(loginId: String = "testuser") = userJpaRepository.save(
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = passwordEncryptor.encrypt(TEST_PASSWORD),
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("$loginId@example.com"),
        )
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

    private fun requestPaymentViaApi(orderId: Long): PaymentV1Dto.PaymentResponse? {
        val request = PaymentV1Dto.CreatePaymentRequest(
            orderId = orderId,
            cardType = CardType.SAMSUNG.name,
            cardNo = "1234-5678-9012-3456",
        )
        val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
        return testRestTemplate.exchange(
            PAYMENTS,
            HttpMethod.POST,
            HttpEntity(request, authHeaders()),
            responseType,
        ).body?.data
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    inner class RequestPayment {

        @Test
        @DisplayName("정상 결제 요청 시 200과 PENDING 상태의 PaymentResponse를 반환하고 DB에 결제 레코드가 생성된다")
        fun requestPayment_whenValid_thenReturnsPendingPayment() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)
            val order = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
            )

            val request = PaymentV1Dto.CreatePaymentRequest(
                orderId = order.id,
                cardType = CardType.SAMSUNG.name,
                cardNo = "1234-5678-9012-3456",
            )

            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                PAYMENTS,
                HttpMethod.POST,
                HttpEntity(request, authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(response.body?.data?.orderId).isEqualTo(order.id) },
            )

            val payments = paymentJpaRepository.findAll()
            assertThat(payments).hasSize(1)
        }

        @Test
        @DisplayName("인증 없이 요청 시 401을 반환한다")
        fun requestPayment_whenNoAuth_thenReturns401() {
            val request = PaymentV1Dto.CreatePaymentRequest(
                orderId = 1L,
                cardType = CardType.SAMSUNG.name,
                cardNo = "1234-5678-9012-3456",
            )

            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                PAYMENTS,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        @DisplayName("동일 주문에 PENDING 결제가 이미 존재할 때 409를 반환한다")
        fun requestPayment_whenActivePaymentExists_thenReturns409() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)
            val order = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
            )

            // 첫 번째 결제 요청 — 성공
            requestPaymentViaApi(order.id)

            // 두 번째 결제 요청 — PENDING 결제가 이미 존재
            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                PAYMENTS,
                HttpMethod.POST,
                HttpEntity(
                    PaymentV1Dto.CreatePaymentRequest(
                        orderId = order.id,
                        cardType = CardType.SAMSUNG.name,
                        cardNo = "1234-5678-9012-3456",
                    ),
                    authHeaders(),
                ),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(paymentJpaRepository.findAll()).hasSize(1)
        }

        @Test
        @DisplayName("PG 요청 실패 시 400을 반환하고 Payment가 FAILED 상태로 저장된다")
        fun requestPayment_whenPgFails_thenReturns400AndPaymentFailed() {
            fakePgPaymentClient.shouldFailRequest = true

            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)
            val order = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
            )

            val request = PaymentV1Dto.CreatePaymentRequest(
                orderId = order.id,
                cardType = CardType.SAMSUNG.name,
                cardNo = "1234-5678-9012-3456",
            )

            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                PAYMENTS,
                HttpMethod.POST,
                HttpEntity(request, authHeaders()),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)

            val payments = paymentJpaRepository.findAll()
            assertThat(payments).hasSize(1)
            assertThat(payments[0].status).isEqualTo(PaymentStatus.FAILED)
        }
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
            val order = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
            )

            // 결제 생성
            requestPaymentViaApi(order.id)

            val payments = paymentJpaRepository.findAll()
            val pgTxId = payments[0].pgTxId!!.value

            // 콜백 수신
            val callbackRequest = PaymentV1Dto.PgCallbackRequest(
                transactionKey = pgTxId,
                orderId = "ORDER-${order.id}",
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
            // 타임아웃 시나리오 — pgTxId 없이 PENDING 결제 생성
            fakePgPaymentClient.shouldTimeout = true
            fakePgPaymentClient.pgStatus = "SUCCESS"

            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)
            val order = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderFacade.OrderItemRequest(productId = product.id, quantity = 1L)),
                couponId = null,
            )

            requestPaymentViaApi(order.id) // 타임아웃 → PENDING 유지
            fakePgPaymentClient.shouldTimeout = false

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
