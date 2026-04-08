package com.loopers.interfaces.api.queue

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Queue V1 API", description = "주문 대기열 API")
interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "주문 대기열에 진입합니다. 이미 진입한 경우 현재 대기 상태를 반환합니다.",
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
    fun enterQueue(
        @Parameter(hidden = true) @RequireAuth authenticatedUser: AuthenticatedUser,
    ): ApiResponse<QueueV1Dto.QueueResponse>

    @Operation(
        summary = "현재 대기 순번 조회",
        description = "현재 사용자의 대기 순번과 전체 대기 인원을 조회합니다. 입장 허용 상태라면 주문용 토큰을 함께 반환합니다.",
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
    fun getQueuePosition(
        @Parameter(hidden = true) @RequireAuth authenticatedUser: AuthenticatedUser,
    ): ApiResponse<QueueV1Dto.QueueResponse>
}
