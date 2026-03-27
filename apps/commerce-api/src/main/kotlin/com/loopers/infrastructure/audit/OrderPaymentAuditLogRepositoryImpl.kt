package com.loopers.infrastructure.audit

import com.loopers.domain.audit.OrderPaymentAuditLogModel
import com.loopers.domain.audit.OrderPaymentAuditLogRepository
import org.springframework.stereotype.Repository

@Repository
class OrderPaymentAuditLogRepositoryImpl(
    private val orderPaymentAuditLogJpaRepository: OrderPaymentAuditLogJpaRepository,
) : OrderPaymentAuditLogRepository {
    override fun save(log: OrderPaymentAuditLogModel): OrderPaymentAuditLogModel =
        orderPaymentAuditLogJpaRepository.save(log)

    override fun findAllByOrderByCreatedAtAsc(): List<OrderPaymentAuditLogModel> =
        orderPaymentAuditLogJpaRepository.findAllByOrderByCreatedAtAsc()
}
