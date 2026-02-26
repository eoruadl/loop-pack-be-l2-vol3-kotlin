package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val userService: UserService,
) {
    fun like(loginId: String, productId: Long): LikeInfo {
        val user = userService.getUserByLoginId(loginId)
        return likeService.like(user.id, productId)
            .let { LikeInfo.from(it) }
    }

    fun unlike(loginId: String, productId: Long) {
        val user = userService.getUserByLoginId(loginId)
        likeService.unlike(user.id, productId)
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
