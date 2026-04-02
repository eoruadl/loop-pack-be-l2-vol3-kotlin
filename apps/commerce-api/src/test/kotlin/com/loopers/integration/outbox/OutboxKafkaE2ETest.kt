package com.loopers.integration.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.application.catalog.CatalogEventOutboxCommand
import com.loopers.application.catalog.CatalogEventOutboxService
import com.loopers.application.couponrequest.CouponIssueRequestInfo
import com.loopers.application.couponrequest.CouponIssueRequestOutboxService
import com.loopers.infrastructure.outbox.OutboxEventPublisher
import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.messaging.coupon.CouponIssueRequestMessage
import com.loopers.testcontainers.KafkaTestContainersConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
import java.util.Properties
import java.util.UUID

@SpringBootTest
@Import(KafkaTestContainersConfig::class)
class OutboxKafkaE2ETest @Autowired constructor(
    private val catalogEventOutboxService: CatalogEventOutboxService,
    private val couponIssueRequestOutboxService: CouponIssueRequestOutboxService,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @Test
    fun `아웃박스에 저장된 catalog 이벤트가 Kafka로 발행된다`() {
        KafkaConsumer<String, ByteArray>(consumerProps("catalog-events")).use { consumer ->
            consumer.subscribe(listOf("catalog-events"))
            consumer.poll(Duration.ofMillis(500))

            catalogEventOutboxService.enqueue(
                CatalogEventOutboxCommand(
                    eventType = CatalogEventType.PRODUCT_LIKED,
                    productId = 101L,
                    actorLoginId = "testuser",
                )
            )

            outboxEventPublisher.publishPendingEvents()

            val record = waitForRecord(consumer)
            val event = objectMapper.readValue(record.value(), CatalogEventMessage::class.java)

            assertThat(record.key()).isEqualTo("101")
            assertThat(event.eventType).isEqualTo(CatalogEventType.PRODUCT_LIKED)
            assertThat(event.productId).isEqualTo(101L)
            assertThat(event.actorLoginId).isEqualTo("testuser")
        }
    }

    @Test
    fun `쿠폰 발급 요청은 쿠폰 전용 파티션으로 Kafka에 발행된다`() {
        KafkaConsumer<String, ByteArray>(consumerProps("coupon-issue-requests")).use { consumer ->
            couponIssueRequestOutboxService.enqueue(
                CouponIssueRequestInfo(
                    requestId = "req-partition-3",
                    couponTemplateId = 3L,
                    userId = 22L,
                    status = "REQUESTED",
                    failureReason = null,
                    createdAt = java.time.ZonedDateTime.now(),
                    updatedAt = java.time.ZonedDateTime.now(),
                )
            )

            val targetPartition = TopicPartition("coupon-issue-requests", 2)
            consumer.assign(listOf(targetPartition))
            consumer.seekToBeginning(listOf(targetPartition))

            outboxEventPublisher.publishPendingEvents()

            val record = waitForRecord(consumer)
            val event = objectMapper.readValue(record.value(), CouponIssueRequestMessage::class.java)

            assertThat(record.key()).isEqualTo("3")
            assertThat(record.partition()).isEqualTo(2)
            assertThat(event.couponTemplateId).isEqualTo(3L)
            assertThat(event.userId).isEqualTo(22L)
        }
    }

    private fun consumerProps(groupIdPrefix: String): Properties =
        Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("BOOTSTRAP_SERVERS"))
            put(ConsumerConfig.GROUP_ID_CONFIG, "$groupIdPrefix-${UUID.randomUUID()}")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
        }

    private fun waitForRecord(consumer: KafkaConsumer<String, ByteArray>): org.apache.kafka.clients.consumer.ConsumerRecord<String, ByteArray> {
        repeat(20) {
            val polled = consumer.poll(Duration.ofMillis(500))
            if (!polled.isEmpty) return polled.first()
        }
        error("Kafka 레코드를 수신하지 못했습니다.")
    }
}
