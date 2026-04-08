package com.loopers.domain.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "tb_payment")
class PaymentModel(
    orderId: Long,
    userId: Long,
    cardType: CardType,
    cardNo: CardNo,
    status: PaymentStatus = PaymentStatus.PENDING,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var cardType: CardType = cardType
        protected set

    @Column(nullable = false)
    var cardNo: CardNo = cardNo
        protected set

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = status
        protected set

    @Column(name = "pg_tx_id", nullable = true)
    var pgTxId: PgTransactionId? = null
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        val now = ZonedDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    private fun preUpdate() {
        updatedAt = ZonedDateTime.now()
    }

    fun complete() {
        check(status == PaymentStatus.PENDING) {
            "$status 상태에서는 완료 처리할 수 없습니다."
        }
        status = PaymentStatus.COMPLETED
    }

    fun fail(failStatus: PaymentStatus = PaymentStatus.FAILED) {
        require(failStatus in listOf(PaymentStatus.FAILED, PaymentStatus.LIMIT_EXCEEDED, PaymentStatus.INVALID_CARD)) {
            "올바르지 않은 실패 상태입니다: $failStatus"
        }
        check(status == PaymentStatus.PENDING) {
            "$status 상태에서는 실패 처리할 수 없습니다."
        }
        status = failStatus
    }

    fun setPgTransactionId(pgTxId: PgTransactionId) {
        this.pgTxId = pgTxId
    }
}
