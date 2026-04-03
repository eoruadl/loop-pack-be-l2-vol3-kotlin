package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderInfo
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentInfo
import com.loopers.application.queue.OrderQueueAdmissionGuard
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import com.loopers.application.useraction.UserActionEvent
import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import com.loopers.interfaces.api.auth.AuthenticatedUser
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.ZonedDateTime

@ExtendWith(MockKExtension::class)
class OrderV1ControllerTest {

    private val orderFacade: OrderFacade = mockk()
    private val paymentFacade: PaymentFacade = mockk()
    private val orderQueueAdmissionGuard: OrderQueueAdmissionGuard = mockk(relaxed = true)
    private val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val controller = OrderV1Controller(orderFacade, paymentFacade, orderQueueAdmissionGuard, applicationEventPublisher)

    @Test
    fun `주문 생성 시 유저 액션 이벤트를 발행한다`() {
        every { orderFacade.createOrder(any(), any(), any()) } returns createOrderInfo()
        every { paymentFacade.requestPayment(any(), any(), any(), any()) } returns createPaymentInfo()

        controller.createOrder(
            AuthenticatedUser("testuser", "1990-01-01"),
            "queue-token",
            OrderV1Dto.CreateOrderRequest(
                items = listOf(OrderV1Dto.CreateOrderRequest.OrderItemRequest(1L, 1L)),
                cardType = CardType.SAMSUNG.name,
                cardNo = "1234-5678-9012-3456",
            ),
        )

        verify {
            orderQueueAdmissionGuard.ensureCreateOrderAllowed("testuser", "queue-token")
            applicationEventPublisher.publishEvent(
                match<UserActionEvent> {
                    it.actionType == UserActionType.ORDER_CREATE &&
                        it.actorLoginId == "testuser" &&
                        it.targetType == UserActionTargetType.ORDER &&
                        it.targetId == 1L
                },
            )
        }
    }

    @Test
    fun `주문 목록 조회 시 유저 액션 이벤트를 발행한다`() {
        every { orderFacade.getOrders(any(), any(), any(), any()) } returns PageImpl(listOf(createOrderInfo()))

        controller.getOrders(
            AuthenticatedUser("testuser", "1990-01-01"),
            LocalDate.now().minusDays(1),
            LocalDate.now(),
            PageRequest.of(0, 10),
        )

        verify {
            applicationEventPublisher.publishEvent(
                match<UserActionEvent> {
                    it.actionType == UserActionType.ORDER_LIST_VIEW &&
                        it.actorLoginId == "testuser" &&
                        it.targetType == UserActionTargetType.ORDER
                },
            )
        }
    }

    @Test
    fun `주문 상세 조회 시 유저 액션 이벤트를 발행한다`() {
        every { orderFacade.getOrderById("testuser", 1L) } returns createOrderInfo()

        controller.getOrderById(
            AuthenticatedUser("testuser", "1990-01-01"),
            1L,
        )

        verify {
            applicationEventPublisher.publishEvent(
                match<UserActionEvent> {
                    it.actionType == UserActionType.ORDER_DETAIL_VIEW &&
                        it.actorLoginId == "testuser" &&
                        it.targetType == UserActionTargetType.ORDER &&
                        it.targetId == 1L
                },
            )
        }
    }

    private fun createOrderInfo(): OrderInfo =
        OrderInfo(
            id = 1L,
            userId = 1L,
            totalAmount = 50_000L,
            status = "PENDING_PAYMENT",
            items = emptyList(),
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )

    private fun createPaymentInfo(): PaymentInfo =
        PaymentInfo(
            id = 1L,
            orderId = 1L,
            userId = 1L,
            cardType = CardType.SAMSUNG.name,
            cardNo = "1234-5678-9012-3456",
            status = PaymentStatus.PENDING,
            pgTransactionId = "pg-tx-123",
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )
}
