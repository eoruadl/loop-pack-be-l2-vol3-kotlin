package com.loopers.interfaces.consumer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.application.couponrequest.CouponIssueRequestProcessingService
import com.loopers.messaging.coupon.CouponIssueRequestMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.kafka.support.Acknowledgment
import java.time.ZonedDateTime

class CouponIssueRequestConsumerTest {

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val processingService: CouponIssueRequestProcessingService = mock()
    private val acknowledgment: Acknowledgment = mock()
    private val consumer = CouponIssueRequestConsumer(objectMapper, processingService)

    @Test
    fun `처음 수신한 쿠폰 발급 요청을 처리하고 ack 한다`() {
        val message = CouponIssueRequestMessage(
            requestId = "req-1",
            couponTemplateId = 10L,
            userId = 1L,
            occurredAt = ZonedDateTime.now(),
        )

        consumer.consume(
            ConsumerRecord("coupon-issue-requests", 0, 0L, "10", objectMapper.writeValueAsBytes(message)),
            acknowledgment,
        )

        verify(processingService).process("req-1")
        verify(acknowledgment).acknowledge()
    }
}
