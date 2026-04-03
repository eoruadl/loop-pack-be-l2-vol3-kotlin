package com.loopers.infrastructure.queue

import com.loopers.application.queue.QueueAdmissionFacade
import com.loopers.application.queue.QueueAdmissionProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Profile("!test")
@Component
class QueueAdmissionScheduler(
    private val queueAdmissionFacade: QueueAdmissionFacade,
    private val queueAdmissionProperties: QueueAdmissionProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.queue.admission.fixed-delay-millis:1000}")
    fun admitWaitingUsers() {
        val admitted = queueAdmissionFacade.admitWaitingUsers(queueAdmissionProperties.batchSize)
        if (admitted.isNotEmpty()) {
            log.info("주문 대기열 입장 처리 완료 - admittedCount={}", admitted.size)
        }
    }
}
