package com.loopers.domain.metrics

interface ProductMetricsRepository {
    fun save(metrics: ProductMetricsModel): ProductMetricsModel
    fun findByProductId(productId: Long): ProductMetricsModel?
}
