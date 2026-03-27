package com.loopers.interfaces.api.product

import com.loopers.application.catalog.CatalogEventOutboxCommand
import com.loopers.application.catalog.CatalogEventOutboxService
import com.loopers.application.product.ProductFacade
import com.loopers.application.useraction.UserActionEvent
import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.interfaces.api.ApiResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val catalogEventOutboxService: CatalogEventOutboxService,
) : ProductV1ApiSpec {

    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(name = "sort", defaultValue = "latest") sort: ProductSortType,
        pageable: Pageable,
    ): ApiResponse<Page<ProductV1Dto.ProductResponse>> =
        productFacade.getProducts(brandId, PageRequest.of(pageable.pageNumber, pageable.pageSize, sort.sort))
            .also {
                applicationEventPublisher.publishEvent(
                    UserActionEvent(
                        actionType = UserActionType.PRODUCT_LIST_VIEW,
                        targetType = UserActionTargetType.PRODUCT,
                        description = "상품 목록 조회",
                    )
                )
            }
            .map { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/{productId}")
    override fun getProductById(@PathVariable productId: Long): ApiResponse<ProductV1Dto.ProductResponse> =
        productFacade.getProductById(productId)
            .also {
                applicationEventPublisher.publishEvent(
                    UserActionEvent(
                        actionType = UserActionType.PRODUCT_DETAIL_VIEW,
                        targetType = UserActionTargetType.PRODUCT,
                        targetId = productId,
                        description = "상품 상세 조회",
                    )
                )
                catalogEventOutboxService.enqueue(
                    CatalogEventOutboxCommand(
                        eventType = CatalogEventType.PRODUCT_VIEWED,
                        productId = productId,
                        actorLoginId = null,
                    )
                )
            }
            .let { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
}
