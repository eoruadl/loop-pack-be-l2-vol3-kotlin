package com.loopers.domain.couponrequest

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "tb_coupon_issue_request")
class CouponIssueRequestModel(
    requestId: String,
    userId: Long,
    couponTemplateId: Long,
    status: CouponIssueRequestStatus = CouponIssueRequestStatus.REQUESTED,
    failureReason: String? = null,
) : BaseEntity() {
    @Column(name = "request_id", nullable = false, unique = true)
    var requestId: String = requestId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_template_id", nullable = false)
    var couponTemplateId: Long = couponTemplateId
        protected set

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: CouponIssueRequestStatus = status
        protected set

    @Column(name = "failure_reason")
    var failureReason: String? = failureReason
        protected set

    fun markIssued() {
        status = CouponIssueRequestStatus.ISSUED
        failureReason = null
    }

    fun markFailed(reason: String) {
        status = CouponIssueRequestStatus.FAILED
        failureReason = reason
    }
}
