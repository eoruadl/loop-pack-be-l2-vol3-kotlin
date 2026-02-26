package com.loopers.domain.order

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
@Table(name = "tb_order")
class OrderModel(
    userId: Long,
    totalAmount: TotalAmount,
    status: OrderStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var userId: Long = userId
        protected set

    @Column(nullable = false)
    var totalAmount: TotalAmount = totalAmount
        protected set

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: OrderStatus = status
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

        val now = ZonedDateTime.now()
        updatedAt = now
    }

    fun updateStatus(newStatus: OrderStatus) {
        status = newStatus
    }
}
