package com.loopers.domain.metrics

import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.messaging.order.OrderEventMessage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductMetricsService(
    private val productMetricsRepository: ProductMetricsRepository,
) {
    @Transactional
    fun apply(event: CatalogEventMessage): ProductMetricsModel {
        val metrics = productMetricsRepository.findByProductId(event.productId)
            ?: ProductMetricsModel(productId = event.productId)

        when (event.eventType) {
            CatalogEventType.PRODUCT_VIEWED -> metrics.increaseViewCount(event.occurredAt)
            CatalogEventType.PRODUCT_LIKED -> metrics.increaseLikeCount(event.occurredAt)
            CatalogEventType.PRODUCT_UNLIKED -> metrics.decreaseLikeCount(event.occurredAt)
        }

        return productMetricsRepository.save(metrics)
    }

    @Transactional
    fun applySales(event: OrderEventMessage): List<ProductMetricsModel> =
        event.items.map { item ->
            val metrics = productMetricsRepository.findByProductId(item.productId)
                ?: ProductMetricsModel(productId = item.productId)
            metrics.increaseSalesCount(item.quantity, event.occurredAt)
            productMetricsRepository.save(metrics)
        }
}
