package com.loopers.domain.brand

import com.loopers.domain.product.Description as ProductDescription
import com.loopers.domain.product.ImageUrl
import com.loopers.domain.product.Name as ProductName
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductInventoryModel
import com.loopers.domain.product.ProductInventoryRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class BrandServiceTest {

    private val brandRepository: BrandRepository = mockk()
    private val productRepository: ProductRepository = mockk()
    private val productInventoryRepository: ProductInventoryRepository = mockk()

    private lateinit var brandService: BrandService

    @BeforeEach
    fun setUp() {
        brandService = BrandService(brandRepository, productRepository, productInventoryRepository)
    }

    private fun createBrandModel() = BrandModel(
        name = Name("Nike"),
        logoImageUrl = LogoImageUrl("test.png"),
        description = Description("테스트 브랜드"),
        address = Address("12345", "서울특별시 중구 테스트길 1", "1층"),
        email = Email("nike@example.com"),
        phoneNumber = PhoneNumber("02-3783-4401"),
        businessNumber = BusinessNumber("123-45-67890"),
    )

    @Nested
    inner class CreateBrand {

        @Test
        fun `브랜드 생성 성공`() {
            // given
            every { brandRepository.existsByName(any()) } returns false
            every { brandRepository.existsByBusinessNumber(BusinessNumber("123-45-67890")) } returns false
            every { brandRepository.save(any()) } answers { firstArg() }

            // when
            val result = brandService.createBrand(
                name = "Nike",
                logoImageUrl = "test.png",
                description = "테스트 브랜드",
                zipCode = "12345",
                roadAddress = "서울특별시 중구 테스트길 1",
                detailAddress = "1층",
                email = "nike@example.com",
                phoneNumber = "02-3783-4401",
                businessNumber = "123-45-67890",
            )

            // then
            assertNotNull(result)
            verify(exactly = 1) { brandRepository.save(any()) }
        }

        @Test
        fun `브랜드명 중복 시 CONFLICT 예외`() {
            // given
            every { brandRepository.existsByName(Name("Nike")) } returns true

            // when
            val exception = assertThrows<CoreException> {
                brandService.createBrand(
                    name = "Nike",
                    logoImageUrl = "test.png",
                    description = "테스트 브랜드",
                    zipCode = "12345",
                    roadAddress = "서울특별시 중구 테스트길 1",
                    detailAddress = "1층",
                    email = "nike@example.com",
                    phoneNumber = "02-3783-4401",
                    businessNumber = "123-45-67890",
                )
            }

            // then
            assertEquals(ErrorType.CONFLICT, exception.errorType)
            verify(exactly = 0) { brandRepository.save(any()) }
        }

        @Test
        fun `사업자등록번호 중복 시 CONFLICT 예외`() {
            // given
            every { brandRepository.existsByName(any()) } returns false
            every { brandRepository.existsByBusinessNumber(BusinessNumber("123-45-67890")) } returns true

            // when
            val exception = assertThrows<CoreException> {
                brandService.createBrand(
                    name = "Nike",
                    logoImageUrl = "test.png",
                    description = "테스트 브랜드",
                    zipCode = "12345",
                    roadAddress = "서울특별시 중구 테스트길 1",
                    detailAddress = "1층",
                    email = "nike@example.com",
                    phoneNumber = "02-3783-4401",
                    businessNumber = "123-45-67890",
                )
            }

            // then
            assertEquals(ErrorType.CONFLICT, exception.errorType)
            verify(exactly = 0) { brandRepository.save(any()) }
        }
    }

    @Nested
    inner class GetBrands {

        @Test
        fun `브랜드 목록 반환`() {
            // given
            val pageable = PageRequest.of(0, 20)
            val brands = listOf(createBrandModel(), createBrandModel())
            every { brandRepository.findAll(pageable) } returns PageImpl(brands, pageable, 2L)

            // when
            val result = brandService.getBrands(pageable)

            // then
            assertEquals(2, result.content.size)
            verify(exactly = 1) { brandRepository.findAll(pageable) }
        }
    }

    @Nested
    inner class GetBrandById {

        @Test
        fun `존재하는 ID로 조회 성공`() {
            // given
            val brand = createBrandModel()
            every { brandRepository.findById(0L) } returns brand

            // when
            val result = brandService.getBrandById(0L)

            // then
            assertNotNull(result)
        }

        @Test
        fun `존재하지 않는 ID 조회 시 NOT_FOUND 예외`() {
            // given
            every { brandRepository.findById(99L) } returns null

            // when
            val exception = assertThrows<CoreException> {
                brandService.getBrandById(99L)
            }

            // then
            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
        }
    }

    @Nested
    inner class UpdateBrand {

        @Test
        fun `존재하지 않는 ID 수정 시 NOT_FOUND 예외`() {
            // given
            every { brandRepository.findById(99L) } returns null

            // when
            val exception = assertThrows<CoreException> {
                brandService.updateBrand(
                    id = 99L,
                    name = "Nike",
                    logoImageUrl = "test.png",
                    description = "업데이트",
                    zipCode = "12345",
                    roadAddress = "서울특별시 중구 테스트길 1",
                    detailAddress = "1층",
                    email = "nike@example.com",
                    phoneNumber = "02-3783-4401",
                    businessNumber = "123-45-67890",
                )
            }

            // then
            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
        }

        @Test
        fun `브랜드명 중복 시 CONFLICT 예외`() {
            // given
            val brand = createBrandModel() // name = "Nike"
            every { brandRepository.findById(0L) } returns brand
            every { brandRepository.existsByName(Name("Adidas")) } returns true

            // when
            val exception = assertThrows<CoreException> {
                brandService.updateBrand(
                    id = 0L,
                    name = "Adidas",
                    logoImageUrl = "test.png",
                    description = "테스트 브랜드",
                    zipCode = "12345",
                    roadAddress = "서울특별시 중구 테스트길 1",
                    detailAddress = "1층",
                    email = "nike@example.com",
                    phoneNumber = "02-3783-4401",
                    businessNumber = "123-45-67890",
                )
            }

            // then
            assertEquals(ErrorType.CONFLICT, exception.errorType)
        }

        @Test
        fun `사업자등록번호 중복 시 CONFLICT 예외`() {
            // given
            val brand = createBrandModel() // businessNumber = "123-45-67890"
            every { brandRepository.findById(0L) } returns brand
            every { brandRepository.existsByBusinessNumber(BusinessNumber("999-88-77766")) } returns true

            // when
            val exception = assertThrows<CoreException> {
                brandService.updateBrand(
                    id = 0L,
                    name = "Nike",
                    logoImageUrl = "test.png",
                    description = "테스트 브랜드",
                    zipCode = "12345",
                    roadAddress = "서울특별시 중구 테스트길 1",
                    detailAddress = "1층",
                    email = "nike@example.com",
                    phoneNumber = "02-3783-4401",
                    businessNumber = "999-88-77766",
                )
            }

            // then
            assertEquals(ErrorType.CONFLICT, exception.errorType)
        }

        @Test
        fun `브랜드 수정 성공`() {
            // given
            val brand = createBrandModel()
            every { brandRepository.findById(0L) } returns brand
            every { brandRepository.existsByName(any()) } returns false
            every { brandRepository.existsByBusinessNumber(BusinessNumber("999-88-77766")) } returns false

            // when
            val result = brandService.updateBrand(
                id = 0L,
                name = "Nike Updated",
                logoImageUrl = "updated.png",
                description = "업데이트된 브랜드",
                zipCode = "99999",
                roadAddress = "새 도로명주소",
                detailAddress = "2층",
                email = "updated@nike.com",
                phoneNumber = "02-1234-5678",
                businessNumber = "999-88-77766",
            )

            // then
            assertEquals("Nike Updated", result.name.value)
            assertEquals("업데이트된 브랜드", result.description.value)
        }
    }

    @Nested
    inner class DeleteBrand {

        @Test
        fun `존재하지 않는 ID 삭제 시 NOT_FOUND 예외`() {
            // given
            every { brandRepository.findById(99L) } returns null

            // when
            val exception = assertThrows<CoreException> {
                brandService.deleteBrand(99L)
            }

            // then
            assertEquals(ErrorType.NOT_FOUND, exception.errorType)
        }

        @Test
        fun `삭제 시 product와 inventory도 soft delete 된다`() {
            // given
            val brand = createBrandModel()
            val product = ProductModel(
                brandId = 0L,
                name = ProductName("Air Max"),
                imageUrl = ImageUrl("test.png"),
                description = ProductDescription("신발"),
                price = Price(100_000L),
            )
            val inventory = ProductInventoryModel(product.id, Stock(10L))
            every { brandRepository.findById(0L) } returns brand
            every { productRepository.findAllByBrandId(0L) } returns listOf(product)
            every { productInventoryRepository.findByProductId(0L) } returns inventory

            // when
            brandService.deleteBrand(0L)

            // then
            assertNotNull(brand.deletedAt)
            assertNotNull(product.deletedAt)
            assertNotNull(inventory.deletedAt)
        }
    }
}
