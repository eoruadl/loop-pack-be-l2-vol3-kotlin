package com.loopers.infrastructure.useraction

import com.loopers.domain.useraction.UserActionLogModel
import com.loopers.domain.useraction.UserActionLogRepository
import org.springframework.stereotype.Repository

@Repository
class UserActionLogRepositoryImpl(
    private val userActionLogJpaRepository: UserActionLogJpaRepository,
) : UserActionLogRepository {
    override fun save(log: UserActionLogModel): UserActionLogModel =
        userActionLogJpaRepository.save(log)

    override fun findAllByOrderByCreatedAtAsc(): List<UserActionLogModel> =
        userActionLogJpaRepository.findAllByOrderByCreatedAtAsc()
}
