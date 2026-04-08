package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.queue.OrderQueueAdmissionGuard
import com.loopers.application.useraction.UserActionEvent
import com.loopers.domain.payment.CardType
import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
    private val paymentFacade: PaymentFacade,
    private val orderQueueAdmissionGuard: OrderQueueAdmissionGuard,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : OrderV1ApiSpec {

    @PostMapping
    override fun createOrder(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @RequestHeader("X-Queue-Token", required = false) queueToken: String?,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val cardType = runCatching { CardType.valueOf(request.cardType) }
            .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 카드 타입입니다: ${request.cardType}") }
        orderQueueAdmissionGuard.ensureCreateOrderAllowed(authenticatedUser.loginId, queueToken)

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
        applicationEventPublisher.publishEvent(
            UserActionEvent(
                actionType = UserActionType.ORDER_CREATE,
                actorLoginId = authenticatedUser.loginId,
                targetType = UserActionTargetType.ORDER,
                targetId = orderInfo.id,
                description = "주문 생성",
            ),
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
            .also {
                applicationEventPublisher.publishEvent(
                    UserActionEvent(
                        actionType = UserActionType.ORDER_LIST_VIEW,
                        actorLoginId = authenticatedUser.loginId,
                        targetType = UserActionTargetType.ORDER,
                        description = "주문 목록 조회",
                    ),
                )
            }
            .map { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/{orderId}")
    override fun getOrderById(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> =
        orderFacade.getOrderById(authenticatedUser.loginId, orderId)
            .also {
                applicationEventPublisher.publishEvent(
                    UserActionEvent(
                        actionType = UserActionType.ORDER_DETAIL_VIEW,
                        actorLoginId = authenticatedUser.loginId,
                        targetType = UserActionTargetType.ORDER,
                        targetId = orderId,
                        description = "주문 상세 조회",
                    ),
                )
            }
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
}
