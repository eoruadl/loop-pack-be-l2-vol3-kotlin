package com.loopers.application.payment

import com.loopers.domain.order.DiscountAmount
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.OriginalAmount
import com.loopers.domain.order.TotalAmount
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgTransactionId
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.ZonedDateTime

@ExtendWith(MockKExtension::class)
class PaymentRecoveryFacadeTest {

    private val paymentRepository: PaymentRepository = mockk()
    private val paymentService: PaymentService = PaymentService(paymentRepository)
    private val orderService: OrderService = mockk()
    private val pgPaymentPort: PgPaymentPort = mockk()

    private lateinit var paymentRecoveryFacade: PaymentRecoveryFacade

    @BeforeEach
    fun setUp() {
        paymentRecoveryFacade = PaymentRecoveryFacade(
            paymentService = paymentService,
            orderService = orderService,
            pgPaymentPort = pgPaymentPort,
        )
    }

    // id defaults to 0 (Long default in Kotlin @GeneratedValue entity)
    private fun createPaymentModel(
        orderId: Long = 10L,
        status: PaymentStatus = PaymentStatus.PENDING,
        pgTxId: String? = null,
    ): PaymentModel {
        val model = PaymentModel(
            orderId = orderId,
            userId = 1L,
            cardType = CardType.SAMSUNG,
            cardNo = CardNo("1234-5678-9012-3456"),
            status = status,
        )
        pgTxId?.let { model.setPgTransactionId(PgTransactionId(it)) }
        val now = ZonedDateTime.now()
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = PaymentModel::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(model, now)
        }
        return model
    }

    private fun createOrderModel(orderId: Long = 10L): OrderModel {
        val model = OrderModel(
            userId = 1L,
            originalAmount = OriginalAmount(50_000L),
            discountAmount = DiscountAmount(0L),
            couponId = null,
            totalAmount = TotalAmount(50_000L),
            status = OrderStatus.PENDING_PAYMENT,
        )
        val now = ZonedDateTime.now()
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = OrderModel::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(model, now)
        }
        return model
    }

    @Nested
    inner class RecoverPayment {

        @Test
        fun `pgTxId 있고 PG 성공 결과 시 COMPLETED 전이 및 Order pay 호출`() {
            val payment = createPaymentModel(pgTxId = "pg-tx-abc") // id = 0
            val order = createOrderModel()

            every { paymentRepository.findById(0L) } returns payment
            every { pgPaymentPort.getPayment("pg-tx-abc", 1L) } returns PgPaymentStatusResponse(
                pgTransactionId = "pg-tx-abc",
                status = "SUCCESS",
                failureCode = null,
            )
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderService.payOrder(10L) } returns order

            paymentRecoveryFacade.recoverPayment(0L)

            verify { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) }
            verify(exactly = 1) { orderService.payOrder(10L) }
        }

        @Test
        fun `pgTxId 있고 PG 실패 결과 시 FAILED 전이`() {
            val payment = createPaymentModel(pgTxId = "pg-tx-abc")

            every { paymentRepository.findById(0L) } returns payment
            every { pgPaymentPort.getPayment("pg-tx-abc", 1L) } returns PgPaymentStatusResponse(
                pgTransactionId = "pg-tx-abc",
                status = "FAILED",
                failureCode = PgFailureCode.UNKNOWN,
            )
            every { paymentRepository.save(any()) } answers { firstArg() }

            paymentRecoveryFacade.recoverPayment(0L)

            verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
        }

        @Test
        fun `pgTxId 없고 PG 주문 조회 성공 시 COMPLETED 전이 및 Order pay 호출`() {
            val payment = createPaymentModel(pgTxId = null)
            val order = createOrderModel()

            every { paymentRepository.findById(0L) } returns payment
            every { pgPaymentPort.getPaymentByOrderId(10L, 1L) } returns PgPaymentStatusResponse(
                pgTransactionId = "pg-tx-new",
                status = "SUCCESS",
                failureCode = null,
            )
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderService.payOrder(10L) } returns order

            paymentRecoveryFacade.recoverPayment(0L)

            verify { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) }
            verify(exactly = 1) { orderService.payOrder(10L) }
        }

        @Test
        fun `pgTxId 없고 PG 주문 조회 결과 없을 때 FAILED 전이`() {
            val payment = createPaymentModel(pgTxId = null)

            every { paymentRepository.findById(0L) } returns payment
            every { pgPaymentPort.getPaymentByOrderId(10L, 1L) } returns null
            every { paymentRepository.save(any()) } answers { firstArg() }

            paymentRecoveryFacade.recoverPayment(0L)

            verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
            verify(exactly = 0) { orderService.payOrder(any()) }
        }

        @Test
        fun `PENDING 아닌 상태의 결제 복구 요청 시 아무 처리도 하지 않는다`() {
            val payment = createPaymentModel(status = PaymentStatus.COMPLETED)

            every { paymentRepository.findById(0L) } returns payment

            paymentRecoveryFacade.recoverPayment(0L)

            verify(exactly = 0) { pgPaymentPort.getPayment(any(), any()) }
            verify(exactly = 0) { pgPaymentPort.getPaymentByOrderId(any(), any()) }
        }
    }
}
