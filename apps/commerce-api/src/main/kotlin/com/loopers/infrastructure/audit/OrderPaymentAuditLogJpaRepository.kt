package com.loopers.infrastructure.audit

import com.loopers.domain.audit.OrderPaymentAuditLogModel
import org.springframework.data.jpa.repository.JpaRepository

interface OrderPaymentAuditLogJpaRepository : JpaRepository<OrderPaymentAuditLogModel, Long> {
    fun findAllByOrderByCreatedAtAsc(): List<OrderPaymentAuditLogModel>
}
