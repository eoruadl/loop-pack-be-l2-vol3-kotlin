package com.loopers.integration.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.messaging.order.OrderEventMessage
import com.loopers.messaging.order.OrderEventType
import com.loopers.testcontainers.KafkaTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import java.time.ZonedDateTime

@SpringBootTest
@Import(KafkaTestContainersConfig::class)
class KafkaMetricsE2ETest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val productMetricsRepository: ProductMetricsRepository,
    private val kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry,
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @BeforeEach
    fun waitForConsumers() {
        waitUntil {
            kafkaListenerEndpointRegistry.listenerContainers.all { it.isRunning }
        }
        Thread.sleep(1_000)
    }

    @Test
    fun `catalog 이벤트를 소비해 like_count와 view_count를 반영한다`() {
        kafkaTemplate.send(
            "catalog-events",
            "1",
            objectMapper.readTree(
                objectMapper.writeValueAsString(
                    CatalogEventMessage(
                        eventId = "catalog-e2e-1",
                        eventType = CatalogEventType.PRODUCT_VIEWED,
                        productId = 1L,
                        actorLoginId = null,
                        occurredAt = ZonedDateTime.now(),
                    )
                )
            )
        ).get()

        kafkaTemplate.send(
            "catalog-events",
            "1",
            objectMapper.readTree(
                objectMapper.writeValueAsString(
                    CatalogEventMessage(
                        eventId = "catalog-e2e-2",
                        eventType = CatalogEventType.PRODUCT_LIKED,
                        productId = 1L,
                        actorLoginId = "testuser",
                        occurredAt = ZonedDateTime.now(),
                    )
                )
            )
        ).get()

        waitUntil {
            val metrics = productMetricsRepository.findByProductId(1L)
            metrics != null && metrics.viewCount == 1L && metrics.likeCount == 1L
        }

        val metrics = productMetricsRepository.findByProductId(1L)!!
        assertThat(metrics.viewCount).isEqualTo(1L)
        assertThat(metrics.likeCount).isEqualTo(1L)
    }

    @Test
    fun `order 이벤트를 소비해 sales_count를 반영한다`() {
        kafkaTemplate.send(
            "order-events",
            "100",
            objectMapper.readTree(
                objectMapper.writeValueAsString(
                    OrderEventMessage(
                        eventId = "order-e2e-1",
                        eventType = OrderEventType.PAYMENT_SUCCEEDED,
                        orderId = 100L,
                        paymentId = 10L,
                        userId = 1L,
                        occurredAt = ZonedDateTime.now(),
                        items = listOf(
                            OrderEventMessage.OrderEventItem(productId = 2L, quantity = 3L),
                        ),
                    )
                )
            )
        ).get()

        waitUntil {
            val metrics = productMetricsRepository.findByProductId(2L)
            metrics != null && metrics.salesCount == 3L
        }

        val metrics = productMetricsRepository.findByProductId(2L)!!
        assertThat(metrics.salesCount).isEqualTo(3L)
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(20) {
            if (condition()) return
            Thread.sleep(500)
        }
        error("조건이 만족되지 않았습니다.")
    }
}
