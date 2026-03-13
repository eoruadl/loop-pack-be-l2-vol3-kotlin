package com.loopers.interfaces.api.product

import com.loopers.application.brand.BrandFacade
import com.loopers.application.product.ProductFacade
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.product.ProductCacheManager
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.like.LikeV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductCacheV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandFacade: BrandFacade,
    private val productFacade: ProductFacade,
    private val productCacheManager: ProductCacheManager,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val PUBLIC_PRODUCTS = "/api/v1/products"
        private const val ADMIN_PRODUCTS = "/api-admin/v1/products"
        private const val TEST_PASSWORD = "Password123!"
        private const val LDAP_VALUE = "loopers.admin"
    }

    private val createdProductIds: MutableList<Long> = mutableListOf()

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        createdProductIds.forEach { productCacheManager.evictDetail(it) }
        productCacheManager.evictAllList()
        createdProductIds.clear()
    }

    private fun createUser(loginId: String = "testuser") = userJpaRepository.save(
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = passwordEncryptor.encrypt(TEST_PASSWORD),
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("$loginId@example.com"),
        )
    )

    private fun createBrand() = brandFacade.createBrand(
        name = "Nike",
        logoImageUrl = "logo.png",
        description = "테스트 브랜드",
        zipCode = "12345",
        roadAddress = "서울특별시 중구 테스트길 1",
        detailAddress = "1층",
        email = "nike@google.com",
        phoneNumber = "02-3783-4401",
        businessNumber = "123-45-67890",
    )

    private fun createProduct(brandId: Long) = productFacade.createProduct(
        brandId = brandId,
        name = "Air Max",
        imageUrl = "image.png",
        description = "설명",
        price = 50_000L,
        quantity = 100L,
    ).also { createdProductIds.add(it.id) }

    private fun authHeaders(loginId: String = "testuser") = HttpHeaders().apply {
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", TEST_PASSWORD)
    }

    private fun adminHeaders() = HttpHeaders().apply {
        set("X-Loopers-Ldap", LDAP_VALUE)
    }

    private fun getProductDetail(id: Long) = testRestTemplate.exchange(
        "$PUBLIC_PRODUCTS/$id",
        HttpMethod.GET,
        HttpEntity<Any>(Unit),
        object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {},
    )

    @DisplayName("likeCount 캐시 동작")
    @Nested
    inner class LikeCountCacheBehavior {

        @Test
        @DisplayName("캐시 없는 상태에서 좋아요 후 조회하면 DB에서 likeCount=1을 반환한다")
        fun likeCount_whenCacheMissAfterLike_thenReturnsLikeCountFromDb() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)

            // 캐시 미저장 상태 확인: evictDetail로 캐시 없음 보장
            productCacheManager.evictDetail(product.id)

            // 좋아요 → DB likeCount=1, 캐시 evict 없음
            testRestTemplate.exchange(
                "$PUBLIC_PRODUCTS/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>>() {},
            )

            // cache miss → DB 조회 → likeCount=1
            val response = getProductDetail(product.id)

            assertThat(response.body?.data?.likeCount).isEqualTo(1L)
        }

        @Test
        @DisplayName("캐시 저장 후 좋아요하면 재조회 시 stale likeCount=0을 반환한다 (결과적 일관성)")
        fun likeCount_whenCacheHitAfterLike_thenReturnsStaleLikeCount() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)

            // 첫 번째 조회: cache miss → DB likeCount=0 → putDetail (캐시 저장)
            getProductDetail(product.id)

            // 좋아요 → DB likeCount=1, 캐시 evict 없음
            testRestTemplate.exchange(
                "$PUBLIC_PRODUCTS/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>>() {},
            )

            // cache HIT → likeCount=0 (stale, 결과적 일관성)
            val response = getProductDetail(product.id)

            assertThat(response.body?.data?.likeCount).isEqualTo(0L)
        }

        @Test
        @DisplayName("좋아요 후 상품 수정(evictDetail) 시 재조회하면 DB 최신 likeCount=1을 반환한다")
        fun likeCount_whenEvictedByUpdate_thenReturnsLatestLikeCountFromDb() {
            createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)

            // 첫 번째 조회: cache miss → DB likeCount=0 → 캐시 저장
            getProductDetail(product.id)

            // 좋아요 → DB likeCount=1, 캐시 evict 없음
            testRestTemplate.exchange(
                "$PUBLIC_PRODUCTS/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>>() {},
            )

            // 상품 수정 → evictDetail (both keys deleted)
            val updateRequest = ProductAdminV1Dto.UpdateProductRequest(
                name = "Air Max Updated",
                imageUrl = "updated.png",
                description = "업데이트된 설명",
                price = 60_000L,
                quantity = 90L,
            )
            testRestTemplate.exchange(
                "$ADMIN_PRODUCTS/${product.id}",
                HttpMethod.PUT,
                HttpEntity(updateRequest, adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>>() {},
            )

            // cache miss → DB likeCount=1 → putDetail
            val response = getProductDetail(product.id)

            assertThat(response.body?.data?.likeCount).isEqualTo(1L)
        }

        @Test
        @DisplayName("좋아요 없는 신규 상품 조회 시 likeCount=0을 반환한다")
        fun likeCount_whenInitialFetch_thenReturns0() {
            val brand = createBrand()
            val product = createProduct(brand.id)

            val response = getProductDetail(product.id)

            assertThat(response.body?.data?.likeCount).isEqualTo(0L)
        }
    }
}
