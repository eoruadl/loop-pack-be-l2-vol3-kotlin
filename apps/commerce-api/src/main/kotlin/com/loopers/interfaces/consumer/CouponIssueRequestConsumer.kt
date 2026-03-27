package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.couponrequest.CouponIssueRequestProcessingService
import com.loopers.messaging.coupon.CouponIssueRequestMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestConsumer(
    private val objectMapper: ObjectMapper,
    private val couponIssueRequestProcessingService: CouponIssueRequestProcessingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${app.kafka.topics.coupon-issue-requests:coupon-issue-requests}"],
        groupId = "\${spring.kafka.consumer.group-id:loopers-default-consumer}",
    )
    fun consume(
        record: ConsumerRecord<String, ByteArray>,
        acknowledgment: Acknowledgment,
    ) {
        val message = objectMapper.readValue(record.value(), CouponIssueRequestMessage::class.java)
        couponIssueRequestProcessingService.process(message.requestId)
        acknowledgment.acknowledge()
        log.info(
            "coupon issue request handled - requestId={}, couponTemplateId={}, userId={}",
            message.requestId,
            message.couponTemplateId,
            message.userId,
        )
    }
}
