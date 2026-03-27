package com.loopers.domain.useraction

interface UserActionLogRepository {
    fun save(log: UserActionLogModel): UserActionLogModel
    fun findAllByOrderByCreatedAtAsc(): List<UserActionLogModel>
}
