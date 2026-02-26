package com.loopers.domain.order

import com.loopers.domain.order.ImageUrl
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.Price
import com.loopers.domain.order.ProductName
import com.loopers.domain.order.Quantity
import com.loopers.domain.order.TotalAmount
import com.loopers.domain.product.Description
import com.loopers.domain.product.ImageUrl as ProductImageUrl
import com.loopers.domain.product.Name
import com.loopers.domain.product.Price as ProductPrice
import com.loopers.domain.product.ProductInventoryModel
import com.loopers.domain.product.ProductInventoryRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class OrderServiceTest {

    private val orderRepository: OrderRepository = mockk()
    private val orderItemService: OrderItemService = mockk()
    private val productRepository: ProductRepository = mockk()
    private val productInventoryRepository: ProductInventoryRepository = mockk()

    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderService = OrderService(orderRepository, orderItemService, productRepository, productInventoryRepository)
    }

    private fun createProductModel(
        brandId: Long = 1L,
        name: String = "뉴발란스 991",
        imageUrl: String = "test.png",
        price: Long = 10_000L,
    ): ProductModel = ProductModel(
        brandId = brandId,
        name = Name(name),
        imageUrl = ProductImageUrl(imageUrl),
        description = Description("신발"),
        price = ProductPrice(price),
    )

    private fun createInventoryModel(
        productId: Long = 1L,
        stock: Long = 100L,
    ): ProductInventoryModel = ProductInventoryModel(productId, Stock(stock))

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
        fun `주문 생성 성공 - 재고 차감 및 주문 항목 저장`() {
            // given
            val product = createProductModel(price = 10_000L)
            val inventory = createInventoryModel(stock = 10L)
            val savedOrder = createOrderModel(userId = 1L, totalAmount = 20_000L)
            val savedItems = listOf(createOrderItemModel(unitPrice = 10_000L, quantity = 2L))

            every { productRepository.findById(1L) } returns product
            every { productInventoryRepository.findByProductId(1L) } returns inventory
            every { orderRepository.save(any()) } returns savedOrder
            every { orderItemService.saveAll(any()) } returns savedItems

            // when
            val (order, items) = orderService.createOrder(
                userId = 1L,
                items = listOf(OrderItemRequest(productId = 1L, quantity = 2L)),
            )

            // then
            assertNotNull(order)
            assertEquals(1, items.size)
            assertEquals(8L, inventory.stock.value)  // 10 - 2 = 8
            verify(exactly = 1) { orderRepository.save(any()) }
            verify(exactly = 1) { orderItemService.saveAll(any()) }
        }

        @Test
        fun `주문 생성 시 총 금액은 단가 x 수량의 합으로 계산된다`() {
            // given - 상품A(5_000 x 2), 상품B(3_000 x 3) → totalAmount = 19_000
            val productA = createProductModel(brandId = 1L, price = 5_000L)
            val productB = createProductModel(brandId = 1L, name = "아디다스", price = 3_000L)
            val inventoryA = createInventoryModel(productId = 1L, stock = 10L)
            val inventoryB = createInventoryModel(productId = 2L, stock = 10L)
            val savedOrder = createOrderModel(totalAmount = 19_000L)

            val orderSlot = slot<OrderModel>()
            every { productRepository.findById(1L) } returns productA
            every { productRepository.findById(2L) } returns productB
            every { productInventoryRepository.findByProductId(1L) } returns inventoryA
            every { productInventoryRepository.findByProductId(2L) } returns inventoryB
            every { orderRepository.save(capture(orderSlot)) } returns savedOrder
            every { orderItemService.saveAll(any()) } returns emptyList()

            // when
            orderService.createOrder(
                userId = 1L,
                items = listOf(
                    OrderItemRequest(productId = 1L, quantity = 2L),
                    OrderItemRequest(productId = 2L, quantity = 3L),
                ),
            )

            // then
            assertEquals(19_000L, orderSlot.captured.totalAmount.value)
        }

        @Test
        fun `존재하지 않는 상품 주문 시 NOT_FOUND 예외`() {
            // given
            every { productRepository.findById(99L) } returns null

            // when
            val exception = assertThrows<CoreException> {
                orderService.createOrder(
                    userId = 1L,
                    items = listOf(OrderItemRequest(productId = 99L, quantity = 1L)),
                )
            }

            // then
            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        fun `재고 정보가 없는 상품 주문 시 NOT_FOUND 예외`() {
            // given
            val product = createProductModel()
            every { productRepository.findById(1L) } returns product
            every { productInventoryRepository.findByProductId(1L) } returns null

            // when
            val exception = assertThrows<CoreException> {
                orderService.createOrder(
                    userId = 1L,
                    items = listOf(OrderItemRequest(productId = 1L, quantity = 1L)),
                )
            }

            // then
            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        fun `재고 부족 시 IllegalArgumentException 발생`() {
            // given
            val product = createProductModel()
            val inventory = createInventoryModel(stock = 1L)
            every { productRepository.findById(1L) } returns product
            every { productInventoryRepository.findByProductId(1L) } returns inventory

            // when & then
            assertThrows<IllegalArgumentException> {
                orderService.createOrder(
                    userId = 1L,
                    items = listOf(OrderItemRequest(productId = 1L, quantity = 5L)),
                )
            }
            verify(exactly = 0) { orderRepository.save(any()) }
        }
    }

    @Nested
    inner class GetOrders {

        @Test
        fun `사용자 주문 목록을 Page로 반환`() {
            // given
            val pageable = PageRequest.of(0, 10)
            val orders = listOf(
                createOrderModel(userId = 1L, totalAmount = 10_000L),
                createOrderModel(userId = 1L, totalAmount = 20_000L),
            )
            val start = ZonedDateTime.now().minusDays(7)
            val end = ZonedDateTime.now()
            every { orderRepository.findAllByUserId(1L, start, end, pageable) } returns PageImpl(orders, pageable, 2L)

            // when
            val result = orderService.getOrders(1L, start, end, pageable)

            // then
            assertEquals(2, result.content.size)
            verify(exactly = 1) { orderRepository.findAllByUserId(1L, start, end, pageable) }
        }
    }

    @Nested
    inner class GetOrderById {

        @Test
        fun `주문 단건 조회 성공`() {
            // given
            val order = createOrderModel(userId = 1L, totalAmount = 10_000L)
            every { orderRepository.findById(1L) } returns order

            // when
            val result = orderService.getOrderById(1L)

            // then
            assertNotNull(result)
            assertEquals(1L, result.userId)
            verify(exactly = 1) { orderRepository.findById(1L) }
        }

        @Test
        fun `존재하지 않는 주문 조회 시 NOT_FOUND 예외`() {
            // given
            every { orderRepository.findById(99L) } returns null

            // when
            val exception = assertThrows<CoreException> {
                orderService.getOrderById(99L)
            }

            // then
            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
        }
    }

    @Nested
    inner class GetAllOrders {

        @Test
        fun `전체 주문 목록을 Page로 반환`() {
            // given
            val pageable = PageRequest.of(0, 10)
            val orders = listOf(
                createOrderModel(userId = 1L, totalAmount = 10_000L),
                createOrderModel(userId = 2L, totalAmount = 30_000L),
            )
            every { orderRepository.findAll(pageable) } returns PageImpl(orders, pageable, 2L)

            // when
            val result = orderService.getAllOrders(pageable)

            // then
            assertEquals(2, result.content.size)
            verify(exactly = 1) { orderRepository.findAll(pageable) }
        }
    }
}
