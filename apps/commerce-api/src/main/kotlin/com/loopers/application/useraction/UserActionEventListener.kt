package com.loopers.application.useraction

import com.loopers.domain.useraction.UserActionLogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionEventListener(
    private val userActionLogService: UserActionLogService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handle(event: UserActionEvent) {
        runCatching {
            userActionLogService.record(
                UserActionLogService.RecordUserActionLogCommand(
                    eventId = event.eventId,
                    actionType = event.actionType,
                    actorLoginId = event.actorLoginId,
                    targetType = event.targetType,
                    targetId = event.targetId,
                    description = event.description,
                    occurredAt = event.occurredAt,
                )
            )
        }.onFailure { throwable ->
            log.warn(
                "유저 액션 로그 적재 실패 - actionType={}, actorLoginId={}, targetType={}, targetId={}, reason={}",
                event.actionType,
                event.actorLoginId,
                event.targetType,
                event.targetId,
                throwable.message,
            )
        }
    }
}
