package com.loopers.application.queue

import com.loopers.domain.queue.ActiveQueueToken
import com.loopers.domain.queue.OrderQueueService
import com.loopers.domain.queue.OrderQueueTokenService
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.math.max

@Component
class QueueAdmissionFacade(
    private val orderQueueService: OrderQueueService,
    private val orderQueueTokenService: OrderQueueTokenService,
    private val queueAdmissionProperties: QueueAdmissionProperties,
) {
    fun admitWaitingUsers(limit: Long): List<ActiveQueueToken> {
        val waitingUserIds = orderQueueService.getWaitingUserIds(limit)
        if (waitingUserIds.isEmpty()) return emptyList()

        val issuedAt = Instant.now()
        val slotMillis = max(
            1L,
            queueAdmissionProperties.fixedDelayMillis / max(waitingUserIds.size.toLong(), 1L),
        )

        val issuedTokens = waitingUserIds.mapIndexed { index, userId ->
            orderQueueTokenService.issueToken(
                userId = userId,
                usableAt = issuedAt.plusMillis(index * slotMillis),
            )
        }
        orderQueueService.removeWaitingUsers(waitingUserIds)
        return issuedTokens
    }
}
