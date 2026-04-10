package com.loopers.application.ranking

import com.loopers.application.brand.BrandInfo
import com.loopers.application.product.ProductInfo
import com.loopers.domain.brand.Address
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BusinessNumber
import com.loopers.domain.brand.Email
import com.loopers.domain.brand.LogoImageUrl
import com.loopers.domain.brand.Name as BrandName
import com.loopers.domain.brand.Description as BrandDescription
import com.loopers.domain.brand.PhoneNumber
import com.loopers.domain.brand.BrandService
import com.loopers.domain.ranking.RankingFinalizedScope
import com.loopers.domain.ranking.RankingFinalizedSnapshotModel
import com.loopers.domain.ranking.RankingTargetType
import com.loopers.domain.product.Description
import com.loopers.domain.product.ImageUrl
import com.loopers.domain.product.Name
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.ranking.ProductRankingRedisRepository
import com.loopers.infrastructure.ranking.RankingFinalizedSnapshotJpaRepository
import com.loopers.infrastructure.ranking.RankingCheckpointSnapshotJpaRepository
import com.loopers.infrastructure.ranking.RankingRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.ZonedDateTime

class ProductRankingFacadeTest {
    private val productRankingRedisRepository: ProductRankingRedisRepository = mock()
    private val rankingFinalizedSnapshotJpaRepository: RankingFinalizedSnapshotJpaRepository = mock()
    private val rankingCheckpointSnapshotJpaRepository: RankingCheckpointSnapshotJpaRepository = mock()
    private val queryService = ProductRankingQueryService(
        productRankingRedisRepository = productRankingRedisRepository,
        rankingFinalizedSnapshotJpaRepository = rankingFinalizedSnapshotJpaRepository,
        rankingCheckpointSnapshotJpaRepository = rankingCheckpointSnapshotJpaRepository,
    )
    private val productService: ProductService = mock()
    private val brandService: BrandService = mock()
    private val rankingFacade = ProductRankingFacade(queryService, productService, brandService)

    @Test
    fun `weekly 랭킹은 DB 확정본 fallback으로 조회할 수 있다`() {
        val asOfDate = LocalDate.of(2026, 4, 9)
        whenever(rankingFinalizedSnapshotJpaRepository.findLatestAsOfDate(RankingTargetType.PRODUCT, RankingRedisKeys.SEGMENT, RankingFinalizedScope.WEEKLY))
            .thenReturn(asOfDate)
        whenever(productRankingRedisRepository.hasKey(RankingRedisKeys.weeklyView(asOfDate))).thenReturn(false)
        whenever(
            rankingFinalizedSnapshotJpaRepository.findAllByTargetTypeAndSegmentKeyAndScopeAndAsOfDate(
                eq(RankingTargetType.PRODUCT),
                eq(RankingRedisKeys.SEGMENT),
                eq(RankingFinalizedScope.WEEKLY),
                eq(asOfDate),
                any(),
            ),
        ).thenReturn(
            PageImpl(
                listOf(
                    RankingFinalizedSnapshotModel(
                        targetType = RankingTargetType.PRODUCT,
                        segmentKey = RankingRedisKeys.SEGMENT,
                        scope = RankingFinalizedScope.WEEKLY,
                        asOfDate = asOfDate,
                        rankPosition = 1L,
                        targetId = 10L,
                        score = 12.0,
                    ),
                ),
                PageRequest.of(0, 20),
                1,
            ),
        )
        whenever(productService.getProductById(10L)).thenReturn(createProductModel(id = 10L))
        whenever(brandService.getBrandById(1L)).thenReturn(createBrandModel())

        val result = rankingFacade.getRankings(RankingType.WEEKLY, page = 1, size = 20, date = null)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().rank).isEqualTo(1L)
        assertThat(result.items.first().product.id).isEqualTo(10L)
    }

    @Test
    fun `attachWeeklyMonthlyRanks는 상품 정보에 두 랭킹을 채운다`() {
        val product = createProductInfo(id = 7L)
        val asOfDate = LocalDate.of(2026, 4, 9)
        whenever(rankingFinalizedSnapshotJpaRepository.findLatestAsOfDate(RankingTargetType.PRODUCT, RankingRedisKeys.SEGMENT, RankingFinalizedScope.WEEKLY))
            .thenReturn(asOfDate)
        whenever(rankingFinalizedSnapshotJpaRepository.findLatestAsOfDate(RankingTargetType.PRODUCT, RankingRedisKeys.SEGMENT, RankingFinalizedScope.MONTHLY))
            .thenReturn(asOfDate)
        whenever(productRankingRedisRepository.hasKey(RankingRedisKeys.weeklyView(asOfDate))).thenReturn(false)
        whenever(productRankingRedisRepository.hasKey(RankingRedisKeys.monthlyView(asOfDate))).thenReturn(false)
        whenever(
            rankingFinalizedSnapshotJpaRepository.findByTargetTypeAndSegmentKeyAndScopeAndAsOfDateAndTargetId(
                RankingTargetType.PRODUCT,
                RankingRedisKeys.SEGMENT,
                RankingFinalizedScope.WEEKLY,
                asOfDate,
                7L,
            ),
        ).thenReturn(
            RankingFinalizedSnapshotModel(
                targetType = RankingTargetType.PRODUCT,
                segmentKey = RankingRedisKeys.SEGMENT,
                scope = RankingFinalizedScope.WEEKLY,
                asOfDate = asOfDate,
                rankPosition = 3L,
                targetId = 7L,
                score = 5.0,
            ),
        )
        whenever(
            rankingFinalizedSnapshotJpaRepository.findByTargetTypeAndSegmentKeyAndScopeAndAsOfDateAndTargetId(
                RankingTargetType.PRODUCT,
                RankingRedisKeys.SEGMENT,
                RankingFinalizedScope.MONTHLY,
                asOfDate,
                7L,
            ),
        ).thenReturn(
            RankingFinalizedSnapshotModel(
                targetType = RankingTargetType.PRODUCT,
                segmentKey = RankingRedisKeys.SEGMENT,
                scope = RankingFinalizedScope.MONTHLY,
                asOfDate = asOfDate,
                rankPosition = 2L,
                targetId = 7L,
                score = 9.0,
            ),
        )

        val enriched = rankingFacade.attachWeeklyMonthlyRanks(product)

        assertThat(enriched.weeklyRank).isEqualTo(3L)
        assertThat(enriched.monthlyRank).isEqualTo(2L)
    }

    @Test
    fun `realtime 랭킹 facade는 rolling redis 결과를 상품 정보와 함께 반환한다`() {
        whenever(
            productRankingRedisRepository.getPageFromRollingKeys(
                any<List<String>>(),
                eq(1),
                eq(20),
            ),
        ).thenReturn(
            ProductRankingRedisRepository.RankingPage(
                content = listOf(
                    ProductRankingRedisRepository.RankingScore(targetId = 3L, score = 5.0, rank = 1L),
                    ProductRankingRedisRepository.RankingScore(targetId = 4L, score = 2.0, rank = 2L),
                ),
                totalCount = 2,
            ),
        )
        whenever(productService.getProductById(3L)).thenReturn(createProductModel(id = 3L, name = "Alpha"))
        whenever(productService.getProductById(4L)).thenReturn(createProductModel(id = 4L, name = "Beta"))
        whenever(brandService.getBrandById(1L)).thenReturn(createBrandModel())

        val result = rankingFacade.getRankings(RankingType.REALTIME, page = 1, size = 20, date = null)

        assertThat(result.items.map { it.product.id }).containsExactly(3L, 4L)
        assertThat(result.items.map { it.rank }).containsExactly(1L, 2L)
    }

    private fun createProductInfo(id: Long, name: String = "Air Max") =
        ProductInfo(
            id = id,
            brandId = 1L,
            name = name,
            imageUrl = "image.png",
            description = "설명",
            price = 50_000L,
            likeCount = 0L,
            brand = BrandInfo(
                id = 1L,
                name = "Nike",
                logoImageUrl = "logo.png",
                description = "브랜드 설명",
                zipCode = "12345",
                roadAddress = "서울",
                detailAddress = "101호",
                email = "nike@example.com",
                phoneNumber = "02-0000-0000",
                businessNumber = "123-45-67890",
                createdAt = ZonedDateTime.now(),
                updatedAt = ZonedDateTime.now(),
            ),
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )

    private fun createBrandModel() =
        BrandModel(
            name = BrandName("Nike"),
            logoImageUrl = LogoImageUrl("logo.png"),
            description = BrandDescription("브랜드 설명"),
            address = Address("12345", "서울", "101호"),
            email = Email("nike@example.com"),
            phoneNumber = PhoneNumber("02-0000-0000"),
            businessNumber = BusinessNumber("123-45-67890"),
        ).also {
            val idField = BrandModel::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.setLong(it, 1L)
            val createdAtField = BrandModel::class.java.superclass.getDeclaredField("createdAt")
            createdAtField.isAccessible = true
            createdAtField.set(it, ZonedDateTime.now())
            val updatedAtField = BrandModel::class.java.superclass.getDeclaredField("updatedAt")
            updatedAtField.isAccessible = true
            updatedAtField.set(it, ZonedDateTime.now())
        }

    private fun createProductModel(id: Long, name: String = "Air Max") =
        ProductModel(
            brandId = 1L,
            name = Name(name),
            imageUrl = ImageUrl("image.png"),
            description = Description("설명"),
            price = Price(50_000L),
        ).also {
            val idField = ProductModel::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.setLong(it, id)
            val createdAtField = ProductModel::class.java.superclass.getDeclaredField("createdAt")
            createdAtField.isAccessible = true
            createdAtField.set(it, ZonedDateTime.now())
            val updatedAtField = ProductModel::class.java.superclass.getDeclaredField("updatedAt")
            updatedAtField.isAccessible = true
            updatedAtField.set(it, ZonedDateTime.now())
        }
}
