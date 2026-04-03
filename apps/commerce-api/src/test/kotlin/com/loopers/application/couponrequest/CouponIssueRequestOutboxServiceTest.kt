package com.loopers.application.couponrequest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventService
import com.loopers.infrastructure.couponrequest.CouponIssueRequestPartitionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.ZonedDateTime

class CouponIssueRequestOutboxServiceTest {

    private val outboxEventService: OutboxEventService = mock()
    private val partitionManager: CouponIssueRequestPartitionManager = mock()
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
    private val service = CouponIssueRequestOutboxService(
        outboxEventService = outboxEventService,
        objectMapper = objectMapper,
        partitionManager = partitionManager,
        topicName = "coupon-issue-requests",
    )

    @Test
    fun `쿠폰 발급 요청을 쿠폰 전용 파티션으로 아웃박스에 저장한다`() {
        whenever(partitionManager.ensurePartition(3L)).thenReturn(2)
        whenever(outboxEventService.save(any())).thenAnswer {
            val command = it.getArgument<OutboxEventService.SaveOutboxEventCommand>(0)
            OutboxEventModel(
                eventId = command.eventId,
                topic = command.topic,
                partition = command.partition,
                eventKey = command.eventKey,
                eventType = command.eventType,
                payload = command.payload,
            )
        }

        val saved = service.enqueue(
            CouponIssueRequestInfo(
                requestId = "req-1",
                couponTemplateId = 3L,
                userId = 10L,
                status = "REQUESTED",
                failureReason = null,
                createdAt = ZonedDateTime.now(),
                updatedAt = ZonedDateTime.now(),
            ),
        )

        assertThat(saved.topic).isEqualTo("coupon-issue-requests")
        assertThat(saved.partition).isEqualTo(2)
        assertThat(saved.eventKey).isEqualTo("3")
        assertThat(saved.payload).contains("\"couponTemplateId\":3")
    }
}
