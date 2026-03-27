package com.loopers.domain.audit

interface OrderPaymentAuditLogRepository {
    fun save(log: OrderPaymentAuditLogModel): OrderPaymentAuditLogModel
    fun findAllByOrderByCreatedAtAsc(): List<OrderPaymentAuditLogModel>
}
