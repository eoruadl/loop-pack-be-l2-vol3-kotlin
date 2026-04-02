package com.loopers.testcontainers

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

@Configuration
class KafkaTestContainersConfig {
    companion object {
        private val kafkaContainer: KafkaContainer = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"),
        ).apply { start() }

        init {
            System.setProperty("BOOTSTRAP_SERVERS", kafkaContainer.bootstrapServers)

            AdminClient.create(mapOf("bootstrap.servers" to kafkaContainer.bootstrapServers)).use { adminClient ->
                runCatching {
                    adminClient.createTopics(
                        listOf(
                            NewTopic("catalog-events", 1, 1),
                            NewTopic("order-events", 1, 1),
                            NewTopic("coupon-issue-requests", 1, 1),
                        )
                    ).all().get()
                }.exceptionOrNull()?.let { throwable ->
                    if (throwable.cause !is TopicExistsException && throwable !is TopicExistsException) {
                        throw throwable
                    }
                }
            }
        }
    }
}
