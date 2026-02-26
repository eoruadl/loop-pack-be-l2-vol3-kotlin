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
import com.loopers.domain.product.ProductInventoryRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemService: OrderItemService,
    private val productRepository: ProductRepository,
    private val productInventoryRepository: ProductInventoryRepository,
) {
    @Transactional
    fun createOrder(userId: Long, items: List<OrderItemRequest>): Pair<OrderModel, List<OrderItemModel>> {
        val orderItems = items.map { req ->
            val product = productRepository.findById(req.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
            val inventory = productInventoryRepository.findByProductId(req.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고 정보를 찾을 수 없습니다.")

            inventory.decreaseStock(req.quantity)

            Triple(product, inventory, req.quantity)
        }

        val totalAmount = orderItems.sumOf { (product, _, qty) -> product.price.value * qty }
        val order = orderRepository.save(
            OrderModel(userId = userId, totalAmount = TotalAmount(totalAmount), status = OrderStatus.PENDING)
        )

        val savedItems = orderItemService.saveAll(
            orderItems.map { (product, _, qty) ->
                OrderItemModel(
                    orderId = order.id,
                    brandId = product.brandId,
                    productId = product.id,
                    quantity = Quantity(qty),
                    unitPrice = Price(product.price.value),
                    productName = ProductName(product.name.value),
                    imageUrl = ImageUrl(product.imageUrl.value),
                )
            }
        )
        return order to savedItems
    }

    @Transactional(readOnly = true)
    fun getOrders(userId: Long, startAt: ZonedDateTime, endAt: ZonedDateTime, pageable: Pageable): Page<OrderModel> =
        orderRepository.findAllByUserId(userId, startAt, endAt, pageable)

    @Transactional(readOnly = true)
    fun getOrderById(id: Long): OrderModel =
        orderRepository.findById(id) ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")

    @Transactional(readOnly = true)
    fun getAllOrders(pageable: Pageable): Page<OrderModel> =
        orderRepository.findAll(pageable)
}

data class OrderItemRequest(
    val productId: Long,
    val quantity: Long,
)
