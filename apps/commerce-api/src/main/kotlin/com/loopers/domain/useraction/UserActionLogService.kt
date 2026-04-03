package com.loopers.domain.useraction

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class UserActionLogService(
    private val userActionLogRepository: UserActionLogRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(command: RecordUserActionLogCommand): UserActionLogModel =
        userActionLogRepository.save(
            UserActionLogModel(
                eventId = command.eventId,
                actionType = command.actionType,
                actorLoginId = command.actorLoginId,
                targetType = command.targetType,
                targetId = command.targetId,
                description = command.description,
                occurredAt = command.occurredAt,
            ),
        )

    @Transactional(readOnly = true)
    fun getAll(): List<UserActionLogModel> =
        userActionLogRepository.findAllByOrderByCreatedAtAsc()

    data class RecordUserActionLogCommand(
        val eventId: String,
        val actionType: UserActionType,
        val actorLoginId: String?,
        val targetType: UserActionTargetType?,
        val targetId: Long?,
        val description: String?,
        val occurredAt: ZonedDateTime,
    )
}
