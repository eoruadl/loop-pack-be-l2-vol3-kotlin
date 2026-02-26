package com.loopers.application.order

import com.loopers.domain.order.OrderItemRequest
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val orderItemService: OrderItemService,
    private val userService: UserService,
) {
    fun createOrder(loginId: String, items: List<OrderItemRequest>): OrderInfo {
        val user = userService.getUserByLoginId(loginId)
        val (order, orderItems) = orderService.createOrder(user.id, items)
        return OrderInfo.from(order, orderItems)
    }

    fun getOrders(loginId: String, startAt: LocalDate, endAt: LocalDate, pageable: Pageable): Page<OrderInfo> {
        val user = userService.getUserByLoginId(loginId)
        val start = startAt.atStartOfDay(ZoneId.systemDefault())
        val end = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault())
        return orderService.getOrders(user.id, start, end, pageable).map { order ->
            val items = orderItemService.getItemsByOrderId(order.id)
            OrderInfo.from(order, items)
        }
    }

    fun getOrderById(loginId: String, orderId: Long): OrderInfo {
        val user = userService.getUserByLoginId(loginId)
        val order = orderService.getOrderById(orderId)
        if (order.userId != user.id) throw CoreException(ErrorType.FORBIDDEN)
        val items = orderItemService.getItemsByOrderId(orderId)
        return OrderInfo.from(order, items)
    }

    fun getAllOrders(pageable: Pageable): Page<OrderInfo> =
        orderService.getAllOrders(pageable).map { order ->
            val items = orderItemService.getItemsByOrderId(order.id)
            OrderInfo.from(order, items)
        }

    fun getAdminOrderById(orderId: Long): OrderInfo {
        val order = orderService.getOrderById(orderId)
        val items = orderItemService.getItemsByOrderId(orderId)
        return OrderInfo.from(order, items)
    }
}
