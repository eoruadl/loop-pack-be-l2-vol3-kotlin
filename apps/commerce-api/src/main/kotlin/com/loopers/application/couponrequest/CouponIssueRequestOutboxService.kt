package com.loopers.application.couponrequest

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventService
import com.loopers.infrastructure.couponrequest.CouponIssueRequestPartitionManager
import com.loopers.messaging.coupon.CouponIssueRequestMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueRequestOutboxService(
    private val outboxEventService: OutboxEventService,
    private val objectMapper: ObjectMapper,
    private val partitionManager: CouponIssueRequestPartitionManager,
    @Value("\${app.kafka.topics.coupon-issue-requests:coupon-issue-requests}")
    private val topicName: String,
) {
    @Transactional
    fun enqueue(requestInfo: CouponIssueRequestInfo): OutboxEventModel {
        val partition = partitionManager.ensurePartition(requestInfo.couponTemplateId)
        val message = CouponIssueRequestMessage(
            requestId = requestInfo.requestId,
            couponTemplateId = requestInfo.couponTemplateId,
            userId = requestInfo.userId,
            occurredAt = requestInfo.createdAt,
        )

        return outboxEventService.save(
            OutboxEventService.SaveOutboxEventCommand(
                eventId = requestInfo.requestId,
                topic = topicName,
                partition = partition,
                eventKey = requestInfo.couponTemplateId.toString(),
                eventType = "COUPON_ISSUE_REQUESTED",
                payload = objectMapper.writeValueAsString(message),
            ),
        )
    }
}
