package com.loopers.application.payment

import com.loopers.domain.order.DiscountAmount
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.OriginalAmount
import com.loopers.domain.order.TotalAmount
import com.loopers.application.order.OrderEventOutboxService
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgTransactionId
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.messaging.order.OrderEventType
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.ZonedDateTime
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class PaymentFacadeTest {

    private val paymentRepository: PaymentRepository = mockk()
    private val paymentService: PaymentService = PaymentService(paymentRepository)
    private val orderService: OrderService = mockk()
    private val userService: UserService = mockk()
    private val pgPaymentPort: PgPaymentPort = mockk()
    private val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val orderEventOutboxService: OrderEventOutboxService = mockk(relaxed = true)

    private lateinit var paymentFacade: PaymentFacade

    @BeforeEach
    fun setUp() {
        paymentFacade = PaymentFacade(
            paymentService = paymentService,
            orderService = orderService,
            userService = userService,
            pgPaymentPort = pgPaymentPort,
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
            applicationEventPublisher = applicationEventPublisher,
            orderEventOutboxService = orderEventOutboxService,
        )
        every { paymentRepository.existsActiveByOrderId(any()) } returns false
    }

    // UserModel.id is in BaseEntity (default = 0)
    private fun createUserModel(): UserModel = UserModel(
        loginId = LoginId("testuser"),
        encryptedPassword = "encrypted",
        name = Name("홍길동"),
        birthDate = BirthDate("1990-01-01"),
        email = Email("test@example.com"),
    )

    private fun createOrderModel(
        // matches default user.id = 0
        userId: Long = 0L,
        totalAmount: Long = 50_000L,
        status: OrderStatus = OrderStatus.PENDING_PAYMENT,
    ): OrderModel {
        val model = OrderModel(
            userId = userId,
            originalAmount = OriginalAmount(totalAmount),
            discountAmount = DiscountAmount(0L),
            couponId = null,
            totalAmount = TotalAmount(totalAmount),
            status = status,
        )
        val now = ZonedDateTime.now()
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = OrderModel::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(model, now)
        }
        return model
    }

    private fun createPaymentModel(
        orderId: Long = 1L,
        userId: Long = 0L,
        status: PaymentStatus = PaymentStatus.PENDING,
    ): PaymentModel {
        val model = PaymentModel(
            orderId = orderId,
            userId = userId,
            cardType = CardType.SAMSUNG,
            cardNo = CardNo("1234-5678-9012-3456"),
            status = status,
        )
        val now = ZonedDateTime.now()
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = PaymentModel::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(model, now)
        }
        return model
    }

    @Nested
    inner class HandleCallback {

        @Test
        fun `이미 COMPLETED인 결제에 콜백 재수신 시 상태 변경 없이 무시된다`() {
            val payment = createPaymentModel(status = PaymentStatus.COMPLETED).also {
                it.setPgTransactionId(PgTransactionId("pg-tx-123"))
            }

            every { paymentRepository.findByPgTransactionId("pg-tx-123") } returns payment

            paymentFacade.handleCallback(
                pgTransactionId = "pg-tx-123",
                pgStatus = "SUCCESS",
                reason = "정상 승인되었습니다.",
            )

            verify(exactly = 0) { paymentRepository.save(any()) }
            verify(exactly = 0) { orderService.payOrder(any()) }
        }

        @Test
        fun `PENDING 결제에 SUCCESS 콜백 수신 시 COMPLETED 전이 및 주문 결제 완료 처리`() {
            val payment = createPaymentModel(orderId = 1L, status = PaymentStatus.PENDING).also {
                it.setPgTransactionId(PgTransactionId("pg-tx-123"))
            }
            val order = createOrderModel()

            every { paymentRepository.findByPgTransactionId("pg-tx-123") } returns payment
            every { paymentRepository.findById(any()) } returns payment
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderService.payOrder(1L) } returns order

            paymentFacade.handleCallback(
                pgTransactionId = "pg-tx-123",
                pgStatus = "SUCCESS",
                reason = "정상 승인되었습니다.",
            )

            verify { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) }
            verify(exactly = 1) { orderService.payOrder(1L) }
            verify {
                orderEventOutboxService.enqueue(
                    match {
                        it.eventType == OrderEventType.PAYMENT_SUCCEEDED &&
                            it.orderId == 1L &&
                            it.paymentId == 0L
                    },
                )
            }
        }
    }

    @Nested
    inner class RequestPayment {

        @Test
        fun `PG 요청 성공 시 PENDING 상태의 PaymentInfo 반환`() {
            val user = createUserModel() // id = 0
            val order = createOrderModel(userId = 0L) // same as user.id
            val payment = createPaymentModel()

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { pgPaymentPort.requestPayment(any()) } returns PgPaymentResponse("pg-tx-123")
            every { paymentRepository.findById(any()) } returns payment

            val result = paymentFacade.requestPayment(
                loginId = "testuser",
                orderId = 1L,
                cardType = CardType.SAMSUNG,
                cardNo = "1234-5678-9012-3456",
            )

            assertEquals(PaymentStatus.PENDING, result.status)
        }

        @Test
        fun `PG 요청 실패 시 Payment FAILED 업데이트 후 BAD_REQUEST CoreException 반환`() {
            val user = createUserModel()
            val order = createOrderModel(userId = 0L)
            val payment = createPaymentModel()

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { pgPaymentPort.requestPayment(any()) } throws PgPaymentFailException("4xx error")
            every { paymentRepository.findById(any()) } returns payment

            val exception = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    loginId = "testuser",
                    orderId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                )
            }

            assertEquals(ErrorType.BAD_REQUEST, exception.errorType)
            verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
        }

        @Test
        fun `PG 타임아웃 시 Payment PENDING 유지 후 INTERNAL_ERROR CoreException 반환`() {
            val user = createUserModel()
            val order = createOrderModel(userId = 0L)

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { pgPaymentPort.requestPayment(any()) } throws PgPaymentTimeoutException("timeout")

            val exception = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    loginId = "testuser",
                    orderId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                )
            }

            assertEquals(ErrorType.INTERNAL_ERROR, exception.errorType)
            // PENDING 유지 — failPayment 미호출, save 1회만 (createPayment 때)
            verify(exactly = 1) { paymentRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 주문 요청 시 NOT_FOUND CoreException 반환`() {
            val user = createUserModel()
            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(99L) } throws CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")

            val exception = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    loginId = "testuser",
                    orderId = 99L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                )
            }

            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
        }

        @Test
        fun `주문 소유자 불일치 시 FORBIDDEN CoreException 반환`() {
            val user = createUserModel() // id = 0
            val order = createOrderModel(userId = 999L) // 다른 유저의 주문

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns order

            val exception = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    loginId = "testuser",
                    orderId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                )
            }

            assertEquals(ErrorType.FORBIDDEN, exception.errorType)
        }

        @Test
        fun `이미 진행 중인 결제가 있을 때 CONFLICT CoreException 반환`() {
            val user = createUserModel()
            val order = createOrderModel(userId = 0L)

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns order
            every { paymentRepository.existsActiveByOrderId(1L) } returns true

            val exception = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    loginId = "testuser",
                    orderId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                )
            }

            assertEquals(ErrorType.CONFLICT, exception.errorType)
            verify(exactly = 0) { pgPaymentPort.requestPayment(any()) }
        }

        @Test
        fun `주문 상태가 PENDING_PAYMENT 아닐 때 BAD_REQUEST CoreException 반환`() {
            val user = createUserModel()
            val order = createOrderModel(userId = 0L, status = OrderStatus.PAID)

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns order

            val exception = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    loginId = "testuser",
                    orderId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                )
            }

            assertEquals(ErrorType.BAD_REQUEST, exception.errorType)
        }
    }
}
