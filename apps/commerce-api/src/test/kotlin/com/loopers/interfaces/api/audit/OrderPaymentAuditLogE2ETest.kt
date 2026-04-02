package com.loopers.interfaces.api.audit

import com.loopers.application.payment.PgFailureCode
import com.loopers.application.payment.PgPaymentFailException
import com.loopers.application.payment.PgPaymentPort
import com.loopers.application.payment.PgPaymentRequest
import com.loopers.application.payment.PgPaymentResponse
import com.loopers.application.payment.PgPaymentStatusResponse
import com.loopers.application.payment.PgPaymentTimeoutException
import com.loopers.application.product.ProductFacade
import com.loopers.domain.audit.OrderPaymentAuditEventType
import com.loopers.domain.brand.BrandService
import com.loopers.domain.payment.CardType
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.audit.OrderPaymentAuditLogJpaRepository
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.interfaces.api.payment.PaymentV1Dto
import com.loopers.utils.DatabaseCleanUp
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderPaymentAuditLogE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandService: BrandService,
    private val productFacade: ProductFacade,
    private val userJpaRepository: UserJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val auditLogJpaRepository: OrderPaymentAuditLogJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
    private val fakePgPaymentClient: FakePgPaymentClient,
) {
    companion object {
        private const val PAYMENTS = "/api/v1/payments"
        private const val ORDERS = "/api/v1/orders"
        private const val TEST_PASSWORD = "Password123!"
        private const val MASKED_CARD_NO = "****-****-****-3456"
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
        var shouldReturnNullByOrderId = false

        override fun requestPayment(request: PgPaymentRequest): PgPaymentResponse {
            if (shouldTimeout) throw PgPaymentTimeoutException("fake timeout")
            if (shouldFailRequest) throw PgPaymentFailException("fake failure")
            return PgPaymentResponse(pgTransactionId = "fake-pg-tx-${request.orderId}")
        }

        override fun getPayment(pgTxId: String, userId: Long): PgPaymentStatusResponse =
            PgPaymentStatusResponse(
                pgTransactionId = pgTxId,
                status = pgStatus,
                failureCode = if (pgStatus == "FAILED") PgFailureCode.UNKNOWN else null,
            )

        override fun getPaymentByOrderId(orderId: Long, userId: Long): PgPaymentStatusResponse? {
            if (shouldReturnNullByOrderId) return null
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
            shouldReturnNullByOrderId = false
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

    private fun createOrderRequest(productId: Long) = OrderV1Dto.CreateOrderRequest(
        items = listOf(OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId = productId, quantity = 1L)),
        cardType = CardType.SAMSUNG.name,
        cardNo = "1234-5678-9012-3456",
    )

    @DisplayName("주문/결제 감사로그")
    @Nested
    inner class AuditLogs {

        @Test
        fun `주문 생성과 결제 요청 성공 시 감사로그가 순서대로 적재된다`() {
            createUser()
            val product = createProduct(createBrand().id)

            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                ORDERS,
                HttpMethod.POST,
                HttpEntity(createOrderRequest(product.id), authHeaders()),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

            val logs = auditLogJpaRepository.findAllByOrderByCreatedAtAsc()
            assertThat(logs.map { it.eventType }).containsExactly(
                OrderPaymentAuditEventType.ORDER_PLACED,
                OrderPaymentAuditEventType.PAYMENT_REQUESTED,
            )
            assertThat(logs.last().maskedCardNo).isEqualTo(MASKED_CARD_NO)
        }

        @Test
        fun `PG 요청 실패 시 결제 요청 실패 감사로그가 적재된다`() {
            createUser()
            val product = createProduct(createBrand().id)
            fakePgPaymentClient.shouldFailRequest = true

            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                ORDERS,
                HttpMethod.POST,
                HttpEntity(createOrderRequest(product.id), authHeaders()),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)

            val logs = auditLogJpaRepository.findAllByOrderByCreatedAtAsc()
            assertThat(logs.map { it.eventType }).containsExactly(
                OrderPaymentAuditEventType.ORDER_PLACED,
                OrderPaymentAuditEventType.PAYMENT_REQUEST_FAILED,
            )
            assertThat(logs.last().maskedCardNo).isEqualTo(MASKED_CARD_NO)
        }

        @Test
        fun `결제 성공 콜백 수신 시 성공 감사로그가 추가된다`() {
            createUser()
            val product = createProduct(createBrand().id)

            val createResponseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            testRestTemplate.exchange(
                ORDERS,
                HttpMethod.POST,
                HttpEntity(createOrderRequest(product.id), authHeaders()),
                createResponseType,
            )

            val payment = paymentJpaRepository.findAll().single()
            val callbackRequest = PaymentV1Dto.PgCallbackRequest(
                transactionKey = payment.pgTxId!!.value,
                orderId = "ORDER-${payment.orderId}",
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
            assertThat(auditLogJpaRepository.findAllByOrderByCreatedAtAsc().map { it.eventType }).contains(
                OrderPaymentAuditEventType.PAYMENT_SUCCEEDED,
            )
        }

        @Test
        fun `결제 실패 콜백 수신 시 실패 감사로그가 추가된다`() {
            createUser()
            val product = createProduct(createBrand().id)

            val createResponseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            testRestTemplate.exchange(
                ORDERS,
                HttpMethod.POST,
                HttpEntity(createOrderRequest(product.id), authHeaders()),
                createResponseType,
            )

            val payment = paymentJpaRepository.findAll().single()
            val callbackRequest = PaymentV1Dto.PgCallbackRequest(
                transactionKey = payment.pgTxId!!.value,
                orderId = "ORDER-${payment.orderId}",
                cardType = CardType.SAMSUNG.name,
                cardNo = "1234-5678-9012-3456",
                amount = 50_000L,
                status = "FAILED",
                reason = "잘못된 카드입니다. 다른 카드를 선택해주세요.",
            )

            val callbackResponseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            val callbackResponse = testRestTemplate.exchange(
                "$PAYMENTS/callback",
                HttpMethod.POST,
                HttpEntity(callbackRequest),
                callbackResponseType,
            )

            assertThat(callbackResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(auditLogJpaRepository.findAllByOrderByCreatedAtAsc().map { it.eventType }).contains(
                OrderPaymentAuditEventType.PAYMENT_FAILED,
            )
        }

        @Test
        fun `결제 복구 성공 시 복구 성공 감사로그가 추가된다`() {
            createUser()
            val product = createProduct(createBrand().id)
            fakePgPaymentClient.shouldTimeout = true

            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                ORDERS,
                HttpMethod.POST,
                HttpEntity(createOrderRequest(product.id), authHeaders()),
                responseType,
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)

            fakePgPaymentClient.shouldTimeout = false

            val payment = paymentJpaRepository.findAll().single()
            val recoverResponseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val recoverResponse = testRestTemplate.exchange(
                "$PAYMENTS/${payment.id}/recover",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                recoverResponseType,
            )

            assertThat(recoverResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(auditLogJpaRepository.findAllByOrderByCreatedAtAsc().map { it.eventType }).contains(
                OrderPaymentAuditEventType.PAYMENT_RECOVERED,
            )
        }

        @Test
        fun `결제 복구 실패 시 복구 실패 감사로그가 추가된다`() {
            createUser()
            val product = createProduct(createBrand().id)
            fakePgPaymentClient.shouldTimeout = true

            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                ORDERS,
                HttpMethod.POST,
                HttpEntity(createOrderRequest(product.id), authHeaders()),
                responseType,
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)

            fakePgPaymentClient.shouldTimeout = false
            fakePgPaymentClient.shouldReturnNullByOrderId = true

            val payment = paymentJpaRepository.findAll().single()
            val recoverResponseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val recoverResponse = testRestTemplate.exchange(
                "$PAYMENTS/${payment.id}/recover",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                recoverResponseType,
            )

            assertThat(recoverResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(auditLogJpaRepository.findAllByOrderByCreatedAtAsc().map { it.eventType }).contains(
                OrderPaymentAuditEventType.PAYMENT_RECOVERY_FAILED,
            )
        }
    }
}
