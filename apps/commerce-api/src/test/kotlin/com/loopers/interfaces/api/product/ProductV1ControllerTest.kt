package com.loopers.interfaces.api.product

import com.loopers.application.catalog.CatalogEventOutboxService
import com.loopers.application.brand.BrandInfo
import com.loopers.application.catalog.CatalogEventOutboxCommand
import com.loopers.application.product.ProductFacade
import com.loopers.application.product.ProductInfo
import com.loopers.application.useraction.UserActionEvent
import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

@ExtendWith(MockKExtension::class)
class ProductV1ControllerTest {

    private val productFacade: ProductFacade = mockk()
    private val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val catalogEventOutboxService: CatalogEventOutboxService = mockk(relaxed = true)
    private val controller = ProductV1Controller(productFacade, applicationEventPublisher, catalogEventOutboxService)

    @Test
    fun `상품 목록 조회 시 유저 액션 이벤트를 발행한다`() {
        every { productFacade.getProducts(any(), any()) } returns PageImpl(listOf(createProductInfo()))

        controller.getProducts(null, ProductSortType.LATEST, PageRequest.of(0, 20))

        verify {
            applicationEventPublisher.publishEvent(
                match<UserActionEvent> {
                    it.actionType == UserActionType.PRODUCT_LIST_VIEW &&
                        it.targetType == UserActionTargetType.PRODUCT
                }
            )
        }
    }

    @Test
    fun `상품 상세 조회 시 유저 액션 이벤트를 발행한다`() {
        every { productFacade.getProductById(1L) } returns createProductInfo()

        controller.getProductById(1L)

        verify {
            applicationEventPublisher.publishEvent(
                match<UserActionEvent> {
                    it.actionType == UserActionType.PRODUCT_DETAIL_VIEW &&
                        it.targetType == UserActionTargetType.PRODUCT &&
                        it.targetId == 1L
                }
            )
        }
        verify {
            catalogEventOutboxService.enqueue(
                match<CatalogEventOutboxCommand> {
                    it.productId == 1L && it.eventType.name == "PRODUCT_VIEWED"
                }
            )
        }
    }

    private fun createProductInfo(): ProductInfo =
        ProductInfo(
            id = 1L,
            brandId = 1L,
            name = "Air Max",
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
}
