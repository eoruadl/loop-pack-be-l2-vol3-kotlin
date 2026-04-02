package com.loopers.application.like

import com.loopers.application.catalog.CatalogEventOutboxCommand
import com.loopers.application.catalog.CatalogEventOutboxService
import com.loopers.domain.like.LikeService
import com.loopers.domain.user.UserService
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Pageable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val userService: UserService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val catalogEventOutboxService: CatalogEventOutboxService,
) {
    @Transactional
    fun like(loginId: String, productId: Long): LikeInfo {
        val user = userService.getUserByLoginId(loginId)
        val (isNew, likeModel) = likeService.like(user.id, productId)
        if (isNew) {
            applicationEventPublisher.publishEvent(
                LikeCountChangedEvent(
                    productId = productId,
                    type = LikeCountChangedEvent.Type.INCREASE,
                )
            )
            catalogEventOutboxService.enqueue(
                CatalogEventOutboxCommand(
                    eventType = CatalogEventType.PRODUCT_LIKED,
                    productId = productId,
                    actorLoginId = user.loginId.value,
                )
            )
        }
        return LikeInfo.from(likeModel)
    }

    @Transactional
    fun unlike(loginId: String, productId: Long) {
        val user = userService.getUserByLoginId(loginId)
        val deleted = likeService.unlike(user.id, productId)
        if (deleted) {
            applicationEventPublisher.publishEvent(
                LikeCountChangedEvent(
                    productId = productId,
                    type = LikeCountChangedEvent.Type.DECREASE,
                )
            )
            catalogEventOutboxService.enqueue(
                CatalogEventOutboxCommand(
                    eventType = CatalogEventType.PRODUCT_UNLIKED,
                    productId = productId,
                    actorLoginId = user.loginId.value,
                )
            )
        }
    }

    fun getLikedProducts(loginId: String, userId: Long, pageable: Pageable): List<LikeInfo> {
        val user = userService.getUserByLoginId(loginId)
        if (user.id != userId) {
            throw CoreException(ErrorType.FORBIDDEN)
        }
        return likeService.getLikedProducts(user.id, pageable)
            .map { LikeInfo.from(it) }
    }
}
