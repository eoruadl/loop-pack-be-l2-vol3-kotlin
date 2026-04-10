package com.loopers.interfaces.api.ranking

import com.loopers.application.product.ProductInfo
import com.loopers.application.ranking.ProductRankingFacade
import java.time.ZonedDateTime

class RankingV1Dto {
    data class RankingPageResponse(
        val items: List<RankingItemResponse>,
        val page: Int,
        val size: Int,
        val totalCount: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(info: ProductRankingFacade.RankingPageInfo) = RankingPageResponse(
                items = info.items.map { RankingItemResponse.from(it) },
                page = info.page,
                size = info.size,
                totalCount = info.totalCount,
                totalPages = info.totalPages,
            )
        }
    }

    data class RankingItemResponse(
        val rank: Long,
        val product: ProductResponse,
    ) {
        companion object {
            fun from(info: ProductRankingFacade.RankingProductInfo) = RankingItemResponse(
                rank = info.rank,
                product = ProductResponse.from(info.product),
            )
        }
    }

    data class ProductResponse(
        val id: Long,
        val brandId: Long,
        val name: String,
        val imageUrl: String,
        val description: String,
        val price: Long,
        val likeCount: Long,
        val brand: BrandResponse,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        data class BrandResponse(
            val id: Long,
            val name: String,
            val logoImageUrl: String,
        )

        companion object {
            fun from(info: ProductInfo) = ProductResponse(
                id = info.id,
                brandId = info.brandId,
                name = info.name,
                imageUrl = info.imageUrl,
                description = info.description,
                price = info.price,
                likeCount = info.likeCount,
                brand = BrandResponse(
                    id = info.brand.id,
                    name = info.brand.name,
                    logoImageUrl = info.brand.logoImageUrl,
                ),
                createdAt = info.createdAt,
                updatedAt = info.updatedAt,
            )
        }
    }
}
