package com.loopers.application.audit

import com.loopers.domain.audit.OrderPaymentAuditLogService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderPaymentAuditLogEventListener(
    private val orderPaymentAuditLogService: OrderPaymentAuditLogService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handle(event: OrderPaymentAuditEvent) {
        orderPaymentAuditLogService.record(
            OrderPaymentAuditLogService.RecordOrderPaymentAuditLogCommand(
                eventId = event.eventId,
                eventType = event.eventType,
                orderId = event.orderId,
                paymentId = event.paymentId,
                userId = event.userId,
                orderStatus = event.orderStatus,
                paymentStatus = event.paymentStatus,
                cardType = event.cardType,
                maskedCardNo = event.maskedCardNo,
                pgTransactionId = event.pgTransactionId,
                reason = event.reason,
                occurredAt = event.occurredAt,
            )
        )
    }
}
