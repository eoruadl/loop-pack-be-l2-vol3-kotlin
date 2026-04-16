package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.idempotency.EventHandledService
import com.loopers.domain.metrics.ProductMetricsService
import com.loopers.messaging.order.OrderEventMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderEventConsumer(
    private val objectMapper: ObjectMapper,
    private val eventHandledService: EventHandledService,
    private val productMetricsService: ProductMetricsService,
    @Value("\${app.kafka.topics.order-events:order-events}")
    private val topicName: String,
) {
    companion object {
        const val HANDLER_NAME = "order-metrics-consumer"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${app.kafka.topics.order-events:order-events}"],
        groupId = "\${spring.kafka.consumer.group-id:loopers-default-consumer}",
    )
    @Transactional
    fun consume(
        record: ConsumerRecord<String, ByteArray>,
        acknowledgment: Acknowledgment,
    ) {
        val event = objectMapper.readValue(record.value(), OrderEventMessage::class.java)
        if (eventHandledService.isHandled(event.eventId, HANDLER_NAME)) {
            acknowledgment.acknowledge()
            return
        }

        productMetricsService.applySales(event)
        eventHandledService.markHandled(event.eventId, topicName, HANDLER_NAME)
        acknowledgment.acknowledge()
        log.info("order event handled - eventId={}, type={}, orderId={}", event.eventId, event.eventType, event.orderId)
    }
}
