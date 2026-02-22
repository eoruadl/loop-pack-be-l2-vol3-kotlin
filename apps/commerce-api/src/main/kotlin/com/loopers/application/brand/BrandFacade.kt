package com.loopers.application.brand

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    fun createBrand(
        name: String,
        logoImageUrl: String,
        description: String,
        zipCode: String,
        roadAddress: String,
        detailAddress: String,
        email: String,
        phoneNumber: String,
        businessNumber: String,
    ): BrandInfo =
        brandService.createBrand(
            name = name,
            logoImageUrl = logoImageUrl,
            description = description,
            zipCode = zipCode,
            roadAddress = roadAddress,
            detailAddress = detailAddress,
            email = email,
            phoneNumber = phoneNumber,
            businessNumber = businessNumber,
        ).let { BrandInfo.from(it) }

    fun getBrands(pageable: Pageable): Page<BrandInfo> =
        brandService.getBrands(pageable).map { BrandInfo.from(it) }

    fun getBrandById(id: Long): BrandInfo =
        brandService.getBrandById(id).let { BrandInfo.from(it) }

    fun updateBrand(
        id: Long,
        name: String,
        logoImageUrl: String,
        description: String,
        zipCode: String,
        roadAddress: String,
        detailAddress: String,
        email: String,
        phoneNumber: String,
        businessNumber: String,
    ): BrandInfo =
        brandService.updateBrand(
            id = id,
            name = name,
            logoImageUrl = logoImageUrl,
            description = description,
            zipCode = zipCode,
            roadAddress = roadAddress,
            detailAddress = detailAddress,
            email = email,
            phoneNumber = phoneNumber,
            businessNumber = businessNumber,
        ).let { BrandInfo.from(it) }

    fun deleteBrand(id: Long) =
        brandService.deleteBrand(id)
}
