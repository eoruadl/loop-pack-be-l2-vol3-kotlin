package com.loopers.domain.audit

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class OrderPaymentAuditLogService(
    private val orderPaymentAuditLogRepository: OrderPaymentAuditLogRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(command: RecordOrderPaymentAuditLogCommand): OrderPaymentAuditLogModel =
        orderPaymentAuditLogRepository.save(
            OrderPaymentAuditLogModel(
                eventId = command.eventId,
                eventType = command.eventType,
                orderId = command.orderId,
                paymentId = command.paymentId,
                userId = command.userId,
                orderStatus = command.orderStatus,
                paymentStatus = command.paymentStatus,
                cardType = command.cardType,
                maskedCardNo = command.maskedCardNo,
                pgTransactionId = command.pgTransactionId,
                reason = command.reason,
                occurredAt = command.occurredAt,
            )
        )

    @Transactional(readOnly = true)
    fun getAll(): List<OrderPaymentAuditLogModel> =
        orderPaymentAuditLogRepository.findAllByOrderByCreatedAtAsc()

    data class RecordOrderPaymentAuditLogCommand(
        val eventId: String,
        val eventType: OrderPaymentAuditEventType,
        val orderId: Long,
        val paymentId: Long?,
        val userId: Long,
        val orderStatus: String?,
        val paymentStatus: String?,
        val cardType: String?,
        val maskedCardNo: String?,
        val pgTransactionId: String?,
        val reason: String?,
        val occurredAt: ZonedDateTime,
    )
}
