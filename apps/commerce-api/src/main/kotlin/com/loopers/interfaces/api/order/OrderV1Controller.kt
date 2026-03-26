package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.payment.CardType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
    private val paymentFacade: PaymentFacade,
) : OrderV1ApiSpec {

    @PostMapping
    override fun createOrder(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val cardType = runCatching { CardType.valueOf(request.cardType) }
            .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 카드 타입입니다: ${request.cardType}") }

        val orderInfo = orderFacade.createOrder(
            loginId = authenticatedUser.loginId,
            items = request.items.map { OrderFacade.OrderItemRequest(it.productId, it.quantity) },
            couponId = request.couponId,
        )
        val paymentInfo = paymentFacade.requestPayment(
            loginId = authenticatedUser.loginId,
            orderId = orderInfo.id,
            cardType = cardType,
            cardNo = request.cardNo,
        )
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderInfo, paymentInfo.id))
    }

    @GetMapping
    override fun getOrders(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @RequestParam startAt: LocalDate,
        @RequestParam endAt: LocalDate,
        pageable: Pageable,
    ): ApiResponse<Page<OrderV1Dto.OrderResponse>> =
        orderFacade.getOrders(authenticatedUser.loginId, startAt, endAt, pageable)
            .map { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/{orderId}")
    override fun getOrderById(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> =
        orderFacade.getOrderById(authenticatedUser.loginId, orderId)
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
}
