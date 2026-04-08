package com.loopers.application.queue

import com.loopers.domain.queue.OrderRequestRateLimitService
import com.loopers.domain.queue.OrderQueueTokenService
import com.loopers.domain.queue.TokenConsumeResult
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class OrderQueueAdmissionGuard(
    private val userService: UserService,
    private val orderQueueTokenService: OrderQueueTokenService,
    private val orderRequestRateLimitService: OrderRequestRateLimitService,
    @Value("\${app.queue.admission.enforce-order-token:true}")
    private val enforceOrderToken: Boolean,
) {
    fun ensureCreateOrderAllowed(loginId: String, queueToken: String?) {
        if (!enforceOrderToken) return

        val user = userService.getUserByLoginId(loginId)
        val requestedToken = queueToken?.takeIf { it.isNotBlank() }
            ?: throw CoreException(ErrorType.FORBIDDEN, "주문 대기열 입장 토큰이 필요합니다.")

        val rateLimitResult = orderRequestRateLimitService.checkLimit(user.id)
        if (!rateLimitResult.allowed) {
            throw CoreException(
                ErrorType.TOO_MANY_REQUESTS,
                "주문 요청이 너무 많습니다. ${rateLimitResult.retryAfterSeconds}초 후 다시 시도해주세요.",
            )
        }

        when (val consumeResult = orderQueueTokenService.consumeIfValid(user.id, requestedToken)) {
            TokenConsumeResult.Success -> Unit
            TokenConsumeResult.Missing,
            TokenConsumeResult.Mismatch,
            TokenConsumeResult.AlreadyConsumed,
            -> throw CoreException(ErrorType.FORBIDDEN, "주문 대기열 입장 토큰이 필요합니다.")

            is TokenConsumeResult.TooEarly -> {
                throw CoreException(
                    ErrorType.TOO_MANY_REQUESTS,
                    "아직 입장 가능 시간이 아닙니다. ${consumeResult.retryAfterSeconds}초 후 다시 시도해주세요.",
                )
            }
        }
    }
}
