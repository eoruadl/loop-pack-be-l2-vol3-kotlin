package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody


@Tag(name = "Payment V1 API", description = "결제 API")
interface PaymentV1ApiSpec {

    @Operation(
        summary = "PG 콜백 수신",
        description = "PG 시스템으로부터 결제 처리 결과 콜백을 수신합니다.",
    )
    fun handleCallback(
        @RequestBody request: PaymentV1Dto.PgCallbackRequest,
    ): ApiResponse<Unit>

    @Operation(
        summary = "결제 수동 복구",
        description = "PENDING 상태의 결제를 PG 상태 조회를 통해 수동으로 복구합니다.",
        parameters = [
            Parameter(
                name = "X-Loopers-LoginId",
                description = "사용자 로그인 ID",
                required = true,
                schema = Schema(type = "string"),
                `in` = ParameterIn.HEADER,
            ),
            Parameter(
                name = "X-Loopers-LoginPw",
                description = "사용자 비밀번호",
                required = true,
                schema = Schema(type = "string"),
                `in` = ParameterIn.HEADER,
            ),
        ],
    )
    fun recoverPayment(
        @Parameter(hidden = true) @RequireAuth authenticatedUser: AuthenticatedUser,
        @PathVariable paymentId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>
}
