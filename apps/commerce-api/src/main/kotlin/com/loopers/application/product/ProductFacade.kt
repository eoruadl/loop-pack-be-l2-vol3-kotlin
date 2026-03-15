package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductInventoryService
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.product.ProductCacheManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productService: ProductService,
    private val productInventoryService: ProductInventoryService,
    private val brandService: BrandService,
    private val productCacheManager: ProductCacheManager,
) {
    @Transactional
    fun createProduct(
        brandId: Long,
        name: String,
        imageUrl: String,
        description: String,
        price: Long,
        quantity: Long,
    ): ProductInfo {
        val product = productService.createProduct(brandId, name, imageUrl, description, price)
        productInventoryService.createInventory(product.id, quantity)
        val brand = BrandInfo.from(brandService.getBrandById(product.brandId))
        productCacheManager.evictAllList()
        return ProductInfo.from(product, brand)
    }

    fun getProducts(brandId: Long?, pageable: Pageable): Page<ProductInfo> {
        val page = pageable.pageNumber
        val sort = pageable.sort

        if (page < ProductCacheManager.LIST_MAX_CACHED_PAGE) {
            productCacheManager.getList(brandId, sort, page)?.let { return it }
        }

        val result = productService.getProducts(brandId, pageable).map { product ->
            val brand = BrandInfo.from(brandService.getBrandById(product.brandId))
            ProductInfo.from(product, brand)
        }

        if (page < ProductCacheManager.LIST_MAX_CACHED_PAGE) {
            productCacheManager.putList(brandId, sort, page, result)
        }

        return result
    }

    fun getProductById(id: Long): ProductInfo {
        productCacheManager.getDetail(id)?.let { return it }
        val product = productService.getProductById(id)
        val brand = BrandInfo.from(brandService.getBrandById(product.brandId))
        return ProductInfo.from(product, brand).also { productCacheManager.putDetail(it) }
    }

    @Transactional
    fun updateProduct(
        id: Long,
        name: String,
        imageUrl: String,
        description: String,
        price: Long,
        quantity: Long,
    ): ProductInfo {
        val product = productService.updateProduct(id, name, imageUrl, description, price)
        productInventoryService.updateStock(id, quantity)
        val brand = BrandInfo.from(brandService.getBrandById(product.brandId))
        productCacheManager.evictDetail(id)
        productCacheManager.evictAllList()
        return ProductInfo.from(product, brand)
    }

    @Transactional
    fun deleteProduct(id: Long) {
        productService.deleteProduct(id)
        productInventoryService.deleteInventory(id)
        productCacheManager.evictDetail(id)
        productCacheManager.evictAllList()
    }
}
