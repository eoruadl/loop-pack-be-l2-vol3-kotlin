package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository
) {

    @Transactional
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
    ): BrandModel {

        if (brandRepository.existsByName(Name(name))) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "이미 존재하는 브랜드명입니다.",
            )
        }

        if (brandRepository.existsByBusinessNumber(BusinessNumber(businessNumber))) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "이미 존재하는 사업자등록번호입니다.",
            )
        }

        val brand = BrandModel(
            name = Name(name),
            logoImageUrl = LogoImageUrl(logoImageUrl),
            description = Description(description),
            address = Address(zipCode, roadAddress, detailAddress),
            email = Email(email),
            phoneNumber = PhoneNumber(phoneNumber),
            businessNumber = BusinessNumber(businessNumber)
        )

        return brandRepository.save(brand)
    }

    @Transactional(readOnly = true)
    fun getBrands(pageable: Pageable = PageRequest.of(0, 20)): Page<BrandModel> {
        return brandRepository.findAll(pageable)
    }

    @Transactional(readOnly = true)
    fun getBrandById(id: Long): BrandModel {
        return brandRepository.findById(id) ?: throw CoreException(
            errorType = ErrorType.NOT_FOUND,
            customMessage = "해당 브랜드를 찾을 수 없습니다.",
        )
    }

    @Transactional
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
    ): BrandModel {

        val brand = brandRepository.findById(id) ?: throw CoreException(
            errorType = ErrorType.NOT_FOUND,
            customMessage = "해당 브랜드를 찾을 수 없습니다.",
        )

        if (brand.name != Name(name) && brandRepository.existsByName(Name(name))) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "이미 존재하는 브랜드명입니다.",
            )
        }

        if (brand.businessNumber != BusinessNumber(businessNumber) && brandRepository.existsByBusinessNumber(BusinessNumber(businessNumber))) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "이미 존재하는 사업자등록번호입니다.",
            )
        }

        brand.update(
            Name(name),
            LogoImageUrl(logoImageUrl),
            Description(description),
            Address(zipCode, roadAddress, detailAddress),
            Email(email),
            PhoneNumber(phoneNumber),
            BusinessNumber(businessNumber)
        )

        return brand
    }

    @Transactional
    fun deleteBrand(id: Long) {
        val brand = brandRepository.findById(id) ?: throw CoreException(
            errorType = ErrorType.NOT_FOUND,
            customMessage = "해당 브랜드를 찾을 수 없습니다."
        )

        brand.delete()
    }
}
