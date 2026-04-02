package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponTemplateModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponTemplateJpaRepository : JpaRepository<CouponTemplateModel, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CouponTemplateModel c
        set c.issuedCount = c.issuedCount + 1
        where c.id = :id
          and (c.issueLimit is null or c.issuedCount < c.issueLimit)
        """
    )
    fun incrementIssuedCountIfAvailable(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CouponTemplateModel c
        set c.issuedCount = c.issuedCount - 1
        where c.id = :id
          and c.issuedCount > 0
        """
    )
    fun decrementIssuedCount(@Param("id") id: Long): Int
}
