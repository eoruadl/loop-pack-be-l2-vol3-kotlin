package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.idempotency.EventHandledService
import com.loopers.domain.ranking.ProductRankingIngestionService
import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.order.OrderEventMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductRankingConsumer(
    private val objectMapper: ObjectMapper,
    private val eventHandledService: EventHandledService,
    private val productRankingIngestionService: ProductRankingIngestionService,
    @Value("\${app.kafka.topics.catalog-events:catalog-events}")
    private val catalogTopicName: String,
    @Value("\${app.kafka.topics.order-events:order-events}")
    private val orderTopicName: String,
) {
    companion object {
        const val CATALOG_HANDLER_NAME = "catalog-ranking-consumer"
        const val ORDER_HANDLER_NAME = "order-ranking-consumer"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${app.kafka.topics.catalog-events:catalog-events}"],
        groupId = "\${app.kafka.consumers.rankings.group-id:loopers-ranking-consumer}",
    )
    @Transactional
    fun consumeCatalog(
        record: ConsumerRecord<String, ByteArray>,
        acknowledgment: Acknowledgment,
    ) {
        val event = objectMapper.readValue(record.value(), CatalogEventMessage::class.java)
        if (eventHandledService.isHandled(event.eventId, CATALOG_HANDLER_NAME)) {
            acknowledgment.acknowledge()
            return
        }

        productRankingIngestionService.apply(event)
        eventHandledService.markHandled(event.eventId, catalogTopicName, CATALOG_HANDLER_NAME)
        acknowledgment.acknowledge()
        log.info("ranking catalog event handled - eventId={}, type={}, productId={}", event.eventId, event.eventType, event.productId)
    }

    @KafkaListener(
        topics = ["\${app.kafka.topics.order-events:order-events}"],
        groupId = "\${app.kafka.consumers.rankings.group-id:loopers-ranking-consumer}",
    )
    @Transactional
    fun consumeOrder(
        record: ConsumerRecord<String, ByteArray>,
        acknowledgment: Acknowledgment,
    ) {
        val event = objectMapper.readValue(record.value(), OrderEventMessage::class.java)
        if (eventHandledService.isHandled(event.eventId, ORDER_HANDLER_NAME)) {
            acknowledgment.acknowledge()
            return
        }

        productRankingIngestionService.apply(event)
        eventHandledService.markHandled(event.eventId, orderTopicName, ORDER_HANDLER_NAME)
        acknowledgment.acknowledge()
        log.info("ranking order event handled - eventId={}, type={}, orderId={}", event.eventId, event.eventType, event.orderId)
    }
}
