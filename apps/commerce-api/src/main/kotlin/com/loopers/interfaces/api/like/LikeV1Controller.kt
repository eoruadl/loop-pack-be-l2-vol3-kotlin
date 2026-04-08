package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.application.useraction.UserActionEvent
import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthenticatedUser
import com.loopers.interfaces.api.auth.RequireAuth
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class LikeV1Controller(
    private val likeFacade: LikeFacade,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : LikeV1ApiSpec {

    @PostMapping("/api/v1/products/{productId}/likes")
    override fun like(
        @PathVariable productId: Long,
        @RequireAuth authenticatedUser: AuthenticatedUser,
    ): ApiResponse<LikeV1Dto.LikeResponse> =
        likeFacade.like(authenticatedUser.loginId, productId)
            .also {
                applicationEventPublisher.publishEvent(
                    UserActionEvent(
                        actionType = UserActionType.PRODUCT_LIKE,
                        actorLoginId = authenticatedUser.loginId,
                        targetType = UserActionTargetType.PRODUCT,
                        targetId = productId,
                        description = "상품 좋아요",
                    ),
                )
            }
            .let { LikeV1Dto.LikeResponse.from(it) }
            .let { ApiResponse.success(it) }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    override fun unlike(
        @PathVariable productId: Long,
        @RequireAuth authenticatedUser: AuthenticatedUser,
    ): ApiResponse<Unit> {
        likeFacade.unlike(authenticatedUser.loginId, productId)
        applicationEventPublisher.publishEvent(
            UserActionEvent(
                actionType = UserActionType.PRODUCT_UNLIKE,
                actorLoginId = authenticatedUser.loginId,
                targetType = UserActionTargetType.PRODUCT,
                targetId = productId,
                description = "상품 좋아요 취소",
            ),
        )
        return ApiResponse.success(Unit)
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    override fun getLikedProducts(
        @PathVariable userId: Long,
        @RequireAuth authenticatedUser: AuthenticatedUser,
        pageable: Pageable,
    ): ApiResponse<List<LikeV1Dto.LikeResponse>> =
        likeFacade.getLikedProducts(authenticatedUser.loginId, userId, pageable)
            .map { LikeV1Dto.LikeResponse.from(it) }
            .let { ApiResponse.success(it) }
}
