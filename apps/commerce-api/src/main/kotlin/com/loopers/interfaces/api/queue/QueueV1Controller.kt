package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val queueFacade: QueueFacade,
) : QueueV1ApiSpec {

    @PostMapping("/enter")
    override fun enterQueue(
        @RequireAuth authenticatedUser: AuthenticatedUser,
    ): ApiResponse<QueueV1Dto.QueueResponse> =
        queueFacade.enter(authenticatedUser.loginId)
            .let { QueueV1Dto.QueueResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/position")
    override fun getQueuePosition(
        @RequireAuth authenticatedUser: AuthenticatedUser,
    ): ApiResponse<QueueV1Dto.QueueResponse> =
        queueFacade.getPosition(authenticatedUser.loginId)
            .let { QueueV1Dto.QueueResponse.from(it) }
            .let { ApiResponse.success(it) }
}
