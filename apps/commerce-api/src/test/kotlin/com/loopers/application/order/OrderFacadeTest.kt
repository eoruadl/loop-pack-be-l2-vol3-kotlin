package com.loopers.application.order

import com.loopers.domain.order.OrderItemRequest
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.user.UserService
import com.loopers.domain.order.ImageUrl
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.Price
import com.loopers.domain.order.ProductName
import com.loopers.domain.order.Quantity
import com.loopers.domain.order.TotalAmount
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.UserModel
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class OrderFacadeTest {

    private val orderService: OrderService = mockk()
    private val orderItemService: OrderItemService = mockk()
    private val userService: UserService = mockk()

    private lateinit var orderFacade: OrderFacade

    @BeforeEach
    fun setUp() {
        orderFacade = OrderFacade(orderService, orderItemService, userService)
    }

    private fun createUserModel(
        loginId: String = "testuser",
    ): UserModel = UserModel(
        loginId = LoginId(loginId),
        encryptedPassword = "encrypted",
        name = Name("홍길동"),
        birthDate = BirthDate("1990-01-01"),
        email = Email("test@example.com"),
    )

    private fun createOrderModel(
        userId: Long = 1L,
        totalAmount: Long = 10_000L,
        status: OrderStatus = OrderStatus.PENDING,
    ): OrderModel {
        val model = OrderModel(userId = userId, totalAmount = TotalAmount(totalAmount), status = status)
        val now = ZonedDateTime.now()
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = OrderModel::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(model, now)
        }
        return model
    }

    private fun createOrderItemModel(
        orderId: Long = 1L,
        productId: Long = 1L,
        brandId: Long = 1L,
        unitPrice: Long = 10_000L,
        quantity: Long = 1L,
    ): OrderItemModel = OrderItemModel(
        orderId = orderId,
        brandId = brandId,
        productId = productId,
        quantity = Quantity(quantity),
        unitPrice = Price(unitPrice),
        productName = ProductName("뉴발란스 991"),
        imageUrl = ImageUrl("test.png"),
    )

    @Nested
    inner class CreateOrder {

        @Test
        fun `주문 생성 시 OrderInfo를 반환한다`() {
            // given
            val user = createUserModel()
            val order = createOrderModel(totalAmount = 10_000L)
            val items = listOf(createOrderItemModel())

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.createOrder(any(), any()) } returns (order to items)

            // when
            val result = orderFacade.createOrder(
                loginId = "testuser",
                items = listOf(OrderItemRequest(productId = 1L, quantity = 1L)),
            )

            // then
            assertNotNull(result)
            assertEquals(order.userId, result.userId)
            assertEquals(order.totalAmount.value, result.totalAmount)
            assertEquals(1, result.items.size)
            verify(exactly = 1) { orderService.createOrder(user.id, any()) }
        }
    }

    @Nested
    inner class GetOrders {

        @Test
        fun `주문 목록 조회 시 Page of OrderInfo를 반환한다`() {
            // given
            val user = createUserModel()
            val pageable = PageRequest.of(0, 10)
            val startAt = LocalDate.of(2026, 1, 1)
            val endAt = LocalDate.of(2026, 2, 28)
            val orders = listOf(
                createOrderModel(userId = 1L, totalAmount = 10_000L),
                createOrderModel(userId = 1L, totalAmount = 20_000L),
            )
            val orderItems = listOf(createOrderItemModel())

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrders(any(), any(), any(), eq(pageable)) } returns PageImpl(orders, pageable, 2L)
            every { orderItemService.getItemsByOrderId(any()) } returns orderItems

            // when
            val result = orderFacade.getOrders("testuser", startAt, endAt, pageable)

            // then
            assertEquals(2, result.content.size)
            assertEquals(10_000L, result.content[0].totalAmount)
            assertEquals(20_000L, result.content[1].totalAmount)
            verify(exactly = orders.size) { orderItemService.getItemsByOrderId(any()) }
        }
    }

    @Nested
    inner class GetOrderById {

        @Test
        fun `본인 주문 조회 시 OrderInfo를 반환한다`() {
            // given
            val user = createUserModel()
            val order = createOrderModel(userId = user.id)
            val items = listOf(createOrderItemModel())

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns order
            every { orderItemService.getItemsByOrderId(1L) } returns items

            // when
            val result = orderFacade.getOrderById("testuser", 1L)

            // then
            assertNotNull(result)
            assertEquals(order.totalAmount.value, result.totalAmount)
        }

        @Test
        fun `타인의 주문 조회 시 FORBIDDEN 예외`() {
            // given
            val user = createUserModel()                     // user.id = 0 (기본값)
            val otherOrder = createOrderModel(userId = 999L) // 다른 유저의 주문

            every { userService.getUserByLoginId("testuser") } returns user
            every { orderService.getOrderById(1L) } returns otherOrder

            // when
            val exception = assertThrows<CoreException> {
                orderFacade.getOrderById("testuser", 1L)
            }

            // then
            assertEquals(ErrorType.FORBIDDEN, exception.errorType)
            verify(exactly = 0) { orderItemService.getItemsByOrderId(any()) }
        }
    }

    @Nested
    inner class GetAllOrders {

        @Test
        fun `전체 주문 목록 조회 시 Page of OrderInfo를 반환한다`() {
            // given
            val pageable = PageRequest.of(0, 10)
            val orders = listOf(
                createOrderModel(userId = 1L, totalAmount = 10_000L),
                createOrderModel(userId = 2L, totalAmount = 30_000L),
            )
            val orderItems = listOf(createOrderItemModel())

            every { orderService.getAllOrders(pageable) } returns PageImpl(orders, pageable, 2L)
            every { orderItemService.getItemsByOrderId(any()) } returns orderItems

            // when
            val result = orderFacade.getAllOrders(pageable)

            // then
            assertEquals(2, result.content.size)
            verify(exactly = orders.size) { orderItemService.getItemsByOrderId(any()) }
        }
    }

    @Nested
    inner class GetAdminOrderById {

        @Test
        fun `어드민 주문 단건 조회 시 OrderInfo를 반환한다`() {
            // given
            val order = createOrderModel(userId = 1L, totalAmount = 50_000L)
            val items = listOf(
                createOrderItemModel(unitPrice = 25_000L, quantity = 2L),
            )

            every { orderService.getOrderById(1L) } returns order
            every { orderItemService.getItemsByOrderId(1L) } returns items

            // when
            val result = orderFacade.getAdminOrderById(1L)

            // then
            assertNotNull(result)
            assertEquals(50_000L, result.totalAmount)
            assertEquals(1, result.items.size)
            assertEquals(50_000L, result.items[0].subTotal)  // 25_000 x 2
        }
    }
}
