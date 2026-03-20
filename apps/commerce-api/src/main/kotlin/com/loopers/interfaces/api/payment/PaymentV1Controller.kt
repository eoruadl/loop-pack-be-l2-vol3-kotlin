package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentRecoveryFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
    private val paymentRecoveryFacade: PaymentRecoveryFacade,
) : PaymentV1ApiSpec {

    @PostMapping("/callback")
    override fun handleCallback(
        @RequestBody request: PaymentV1Dto.PgCallbackRequest,
    ): ApiResponse<Unit> {
        paymentFacade.handleCallback(
            pgTransactionId = request.transactionKey,
            pgStatus = request.status,
            reason = request.reason,
        )
        @Suppress("UNCHECKED_CAST")
        return ApiResponse.success() as ApiResponse<Unit>
    }

    @PostMapping("/{paymentId}/recover")
    override fun recoverPayment(
        @RequireAuth authenticatedUser: AuthenticatedUser,
        @PathVariable paymentId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        paymentRecoveryFacade.recoverPayment(paymentId)
        return paymentRecoveryFacade.getPaymentById(paymentId)
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
