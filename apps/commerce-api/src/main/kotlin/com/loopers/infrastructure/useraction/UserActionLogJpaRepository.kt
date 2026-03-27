package com.loopers.infrastructure.useraction

import com.loopers.domain.useraction.UserActionLogModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserActionLogJpaRepository : JpaRepository<UserActionLogModel, Long> {
    fun findAllByOrderByCreatedAtAsc(): List<UserActionLogModel>
}
