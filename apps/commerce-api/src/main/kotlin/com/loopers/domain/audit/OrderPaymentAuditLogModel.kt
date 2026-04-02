package com.loopers.domain.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "tb_order_payment_audit_log")
class OrderPaymentAuditLogModel(
    eventId: String,
    eventType: OrderPaymentAuditEventType,
    orderId: Long,
    paymentId: Long?,
    userId: Long,
    orderStatus: String?,
    paymentStatus: String?,
    cardType: String?,
    maskedCardNo: String?,
    pgTransactionId: String?,
    reason: String?,
    occurredAt: ZonedDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: String = eventId
        protected set

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var eventType: OrderPaymentAuditEventType = eventType
        protected set

    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(name = "payment_id")
    var paymentId: Long? = paymentId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "order_status")
    var orderStatus: String? = orderStatus
        protected set

    @Column(name = "payment_status")
    var paymentStatus: String? = paymentStatus
        protected set

    @Column(name = "card_type")
    var cardType: String? = cardType
        protected set

    @Column(name = "masked_card_no")
    var maskedCardNo: String? = maskedCardNo
        protected set

    @Column(name = "pg_tx_id")
    var pgTransactionId: String? = pgTransactionId
        protected set

    @Column(name = "reason")
    var reason: String? = reason
        protected set

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: ZonedDateTime = occurredAt
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        createdAt = ZonedDateTime.now()
    }
}
