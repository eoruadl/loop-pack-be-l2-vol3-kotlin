package com.loopers.application.like

import com.loopers.domain.product.ProductService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class LikeCountChangedEventListenerTest {

    private val productService: ProductService = mock()
    private val listener = LikeCountChangedEventListener(productService)

    @Test
    fun `좋아요 증가 이벤트를 받으면 상품 좋아요 수를 증가시킨다`() {
        listener.handle(LikeCountChangedEvent(productId = 1L, type = LikeCountChangedEvent.Type.INCREASE))

        verify(productService).incrementLikeCount(1L)
    }

    @Test
    fun `좋아요 집계 반영 실패가 발생해도 예외를 전파하지 않는다`() {
        doThrow(IllegalStateException("boom")).`when`(productService).incrementLikeCount(1L)

        assertDoesNotThrow {
            listener.handle(LikeCountChangedEvent(productId = 1L, type = LikeCountChangedEvent.Type.INCREASE))
        }
    }
}
