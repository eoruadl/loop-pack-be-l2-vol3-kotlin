package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetricsModel
import com.loopers.domain.metrics.ProductMetricsRepository
import org.springframework.stereotype.Repository

@Repository
class ProductMetricsRepositoryImpl(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {
    override fun save(metrics: ProductMetricsModel): ProductMetricsModel =
        productMetricsJpaRepository.save(metrics)

    override fun findByProductId(productId: Long): ProductMetricsModel? =
        productMetricsJpaRepository.findByProductId(productId)
}
