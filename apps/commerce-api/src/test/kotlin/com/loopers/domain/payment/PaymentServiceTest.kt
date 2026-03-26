package com.loopers.domain.payment

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
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(MockKExtension::class)
class PaymentServiceTest {

    private val paymentRepository: PaymentRepository = mockk()
    private lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentService = PaymentService(paymentRepository)
    }

    private fun createPaymentModel(
        orderId: Long = 1L,
        userId: Long = 1L,
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
    inner class CreatePayment {

        @Test
        fun `정상 생성 시 Repository save 호출 후 PaymentModel 반환`() {
            every { paymentRepository.existsActiveByOrderId(1L) } returns false
            every { paymentRepository.save(any()) } answers { firstArg() }

            val result = paymentService.createPayment(
                orderId = 1L,
                userId = 1L,
                cardType = CardType.SAMSUNG,
                cardNo = "1234-5678-9012-3456",
            )

            assertNotNull(result)
            assertEquals(PaymentStatus.PENDING, result.status)
            verify(exactly = 1) { paymentRepository.save(any()) }
        }

        @Test
        fun `진행 중인 결제가 이미 존재할 때 CONFLICT CoreException 발생`() {
            every { paymentRepository.existsActiveByOrderId(1L) } returns true

            val exception = assertThrows<CoreException> {
                paymentService.createPayment(
                    orderId = 1L,
                    userId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                )
            }

            assertEquals(ErrorType.CONFLICT, exception.errorType)
            verify(exactly = 0) { paymentRepository.save(any()) }
        }
    }

    @Nested
    inner class GetPaymentById {

        @Test
        fun `존재하는 결제 조회 시 반환한다`() {
            val payment = createPaymentModel()
            every { paymentRepository.findById(1L) } returns payment

            val result = paymentService.getPaymentById(1L)

            assertNotNull(result)
            assertEquals(PaymentStatus.PENDING, result.status)
        }

        @Test
        fun `존재하지 않는 결제 조회 시 NOT_FOUND CoreException 발생`() {
            every { paymentRepository.findById(99L) } returns null

            val exception = assertThrows<CoreException> {
                paymentService.getPaymentById(99L)
            }

            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
        }
    }

    @Nested
    inner class GetPaymentByOrderId {

        @Test
        fun `주문 ID로 결제 조회 시 존재하면 반환한다`() {
            val payment = createPaymentModel(orderId = 10L)
            every { paymentRepository.findByOrderId(10L) } returns payment

            val result = paymentService.getPaymentByOrderId(10L)

            assertNotNull(result)
        }

        @Test
        fun `주문 ID로 결제 조회 시 없으면 null 반환`() {
            every { paymentRepository.findByOrderId(99L) } returns null

            val result = paymentService.getPaymentByOrderId(99L)

            assertNull(result)
        }
    }

    @Nested
    inner class FindPendingPaymentsOlderThan {

        @Test
        fun `PENDING 결제 목록 반환 검증`() {
            val threshold = ZonedDateTime.now().minusSeconds(30)
            val payments = listOf(
                createPaymentModel(orderId = 1L),
                createPaymentModel(orderId = 2L),
            )
            every { paymentRepository.findPendingOlderThan(threshold) } returns payments

            val result = paymentService.findPendingPaymentsOlderThan(threshold)

            assertEquals(2, result.size)
            verify(exactly = 1) { paymentRepository.findPendingOlderThan(threshold) }
        }
    }
}
