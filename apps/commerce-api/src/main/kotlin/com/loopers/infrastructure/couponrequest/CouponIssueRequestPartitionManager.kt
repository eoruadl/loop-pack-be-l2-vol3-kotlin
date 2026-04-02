package com.loopers.infrastructure.couponrequest

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.errors.TopicExistsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class CouponIssueRequestPartitionManager(
    private val kafkaProperties: KafkaProperties,
    @Value("\${app.kafka.topics.coupon-issue-requests:coupon-issue-requests}")
    private val topicName: String,
    @Value("\${app.kafka.topics.coupon-issue-requests-replication-factor:1}")
    private val replicationFactor: Short,
) {
    private val knownPartitionCount = AtomicInteger(0)

    fun ensurePartition(couponTemplateId: Long): Int {
        require(couponTemplateId > 0) { "couponTemplateId must be positive: $couponTemplateId" }
        require(couponTemplateId <= Int.MAX_VALUE.toLong()) {
            "couponTemplateId is too large for Kafka partition assignment: $couponTemplateId"
        }

        val desiredPartitionCount = couponTemplateId.toInt()
        if (knownPartitionCount.get() >= desiredPartitionCount) {
            return desiredPartitionCount - 1
        }

        synchronized(this) {
            if (knownPartitionCount.get() >= desiredPartitionCount) {
                return desiredPartitionCount - 1
            }

            AdminClient.create(
                mapOf(
                    CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers.joinToString(","),
                ) + kafkaProperties.properties
            ).use { adminClient ->
                val existingPartitionCount = describePartitionCount(adminClient)
                when {
                    existingPartitionCount == 0 -> {
                        createTopic(adminClient, desiredPartitionCount)
                        knownPartitionCount.set(desiredPartitionCount)
                    }

                    existingPartitionCount < desiredPartitionCount -> {
                        adminClient.createPartitions(
                            mapOf(topicName to NewPartitions.increaseTo(desiredPartitionCount))
                        ).all().get()
                        knownPartitionCount.set(desiredPartitionCount)
                    }

                    else -> {
                        knownPartitionCount.set(existingPartitionCount)
                    }
                }
            }
        }

        return desiredPartitionCount - 1
    }

    private fun describePartitionCount(adminClient: AdminClient): Int =
        runCatching {
            adminClient.describeTopics(listOf(topicName))
                .allTopicNames()
                .get()[topicName]
                ?.partitions()
                ?.size
                ?: 0
        }.getOrDefault(0)

    private fun createTopic(adminClient: AdminClient, partitions: Int) {
        runCatching {
            adminClient.createTopics(
                listOf(NewTopic(topicName, partitions, replicationFactor))
            ).all().get()
        }.exceptionOrNull()?.let { throwable ->
            if (throwable.cause !is TopicExistsException && throwable !is TopicExistsException) {
                throw throwable
            }
        }
    }
}
