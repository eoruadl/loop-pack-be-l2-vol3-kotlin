package com.loopers.application.like

import com.loopers.domain.product.ProductService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeCountChangedEventListener(
    private val productService: ProductService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handle(event: LikeCountChangedEvent) {
        runCatching {
            when (event.type) {
                LikeCountChangedEvent.Type.INCREASE -> productService.incrementLikeCount(event.productId)
                LikeCountChangedEvent.Type.DECREASE -> productService.decrementLikeCount(event.productId)
            }
        }.onFailure { throwable ->
            log.warn(
                "좋아요 집계 반영 실패 - productId={}, type={}, reason={}",
                event.productId,
                event.type,
                throwable.message,
            )
        }
    }
}
