package com.loopers.application.queue

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.queue.admission")
data class QueueAdmissionProperties(
    val batchSize: Long = 20,
    val fixedDelayMillis: Long = 1_000,
    val tokenTtl: Duration = Duration.ofMinutes(5),
    val orderRateLimitMaxRequests: Long = 2,
    val orderRateLimitWindow: Duration = Duration.ofSeconds(1),
)
