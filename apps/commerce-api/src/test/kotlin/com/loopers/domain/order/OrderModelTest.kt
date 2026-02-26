package com.loopers.domain.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class OrderModelTest {

    @Nested
    inner class Create {

        @Test
        fun `유효한 파라미터 입력 시 생성한다`() {
            assertDoesNotThrow {
                OrderModel(
                    userId = 1L,
                    totalAmount = TotalAmount(10000),
                    status = OrderStatus.PENDING,
                )
            }
        }
    }

    @Nested
    inner class UpdateStatus {

        @Test
        fun `주문 상태를 변경한다`() {
            val order = OrderModel(
                userId = 1L,
                totalAmount = TotalAmount(10000),
                status = OrderStatus.PENDING,
            )

            order.updateStatus(OrderStatus.PAID)

            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }
    }
}
