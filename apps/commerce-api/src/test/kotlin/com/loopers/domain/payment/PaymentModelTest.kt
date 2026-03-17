package com.loopers.domain.payment

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class PaymentModelTest {

    private fun createPaymentModel(
        orderId: Long = 1L,
        userId: Long = 1L,
        cardType: CardType = CardType.SAMSUNG,
        cardNo: String = "1234-5678-9012-3456",
        status: PaymentStatus = PaymentStatus.PENDING,
    ): PaymentModel {
        val model = PaymentModel(
            orderId = orderId,
            userId = userId,
            cardType = cardType,
            cardNo = CardNo(cardNo),
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
    inner class Create {

        @Test
        fun `정상 생성 시 PENDING 상태로 시작한다`() {
            val payment = createPaymentModel()
            assertEquals(PaymentStatus.PENDING, payment.status)
        }

        @Test
        fun `userId, orderId, cardType, cardNo가 올바르게 설정된다`() {
            val payment = createPaymentModel(orderId = 10L, userId = 5L)
            assertEquals(10L, payment.orderId)
            assertEquals(5L, payment.userId)
            assertEquals(CardType.SAMSUNG, payment.cardType)
        }
    }

    @Nested
    inner class Complete {

        @Test
        fun `PENDING 상태에서 complete 호출 시 COMPLETED 전이 성공`() {
            val payment = createPaymentModel()
            payment.complete()
            assertEquals(PaymentStatus.COMPLETED, payment.status)
        }

        @Test
        fun `COMPLETED 상태에서 complete 재호출 시 IllegalStateException`() {
            val payment = createPaymentModel(status = PaymentStatus.COMPLETED)
            assertThrows<IllegalStateException> {
                payment.complete()
            }
        }
    }

    @Nested
    inner class Fail {

        @Test
        fun `PENDING 상태에서 fail 호출 시 FAILED 전이 성공`() {
            val payment = createPaymentModel()
            payment.fail()
            assertEquals(PaymentStatus.FAILED, payment.status)
        }

        @Test
        fun `PENDING 상태에서 fail(LIMIT_EXCEEDED) 호출 시 LIMIT_EXCEEDED 전이 성공`() {
            val payment = createPaymentModel()
            payment.fail(PaymentStatus.LIMIT_EXCEEDED)
            assertEquals(PaymentStatus.LIMIT_EXCEEDED, payment.status)
        }

        @Test
        fun `PENDING 상태에서 fail(INVALID_CARD) 호출 시 INVALID_CARD 전이 성공`() {
            val payment = createPaymentModel()
            payment.fail(PaymentStatus.INVALID_CARD)
            assertEquals(PaymentStatus.INVALID_CARD, payment.status)
        }

        @Test
        fun `FAILED 상태에서 fail 재호출 시 IllegalStateException`() {
            val payment = createPaymentModel(status = PaymentStatus.FAILED)
            assertThrows<IllegalStateException> {
                payment.fail()
            }
        }

        @Test
        fun `COMPLETED 상태에서 fail 호출 시 IllegalStateException`() {
            val payment = createPaymentModel(status = PaymentStatus.COMPLETED)
            assertThrows<IllegalStateException> {
                payment.fail()
            }
        }
    }

    @Nested
    inner class SetPgTransactionId {

        @Test
        fun `pgTxId 설정 성공`() {
            val payment = createPaymentModel()
            assertDoesNotThrow {
                payment.setPgTransactionId(PgTransactionId("pg-tx-abc123"))
            }
            assertEquals("pg-tx-abc123", payment.pgTxId?.value)
        }
    }
}
