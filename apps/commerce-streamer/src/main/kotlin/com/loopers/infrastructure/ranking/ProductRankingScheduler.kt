package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingSnapshotService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ProductRankingScheduler(
    private val productRankingSnapshotService: ProductRankingSnapshotService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.ranking.rollup.hourly-cron:0 1 * * * *}", zone = "Asia/Seoul")
    fun materializePreviousHour() {
        runCatching { productRankingSnapshotService.materializePreviousHour() }
            .onFailure { log.error("failed to materialize previous hour ranking bucket", it) }
    }

    @Scheduled(cron = "\${app.ranking.finalize.daily-cron:0 10 0 * * *}", zone = "Asia/Seoul")
    fun finalizePreviousDay() {
        runCatching { productRankingSnapshotService.finalizePreviousDay() }
            .onFailure { log.error("failed to finalize ranking snapshots", it) }
    }

    @Scheduled(cron = "\${app.ranking.checkpoint.cron:0 */5 * * * *}", zone = "Asia/Seoul")
    fun captureRollingCheckpoint() {
        runCatching { productRankingSnapshotService.captureRollingCheckpoint() }
            .onFailure { log.error("failed to capture rolling ranking checkpoint", it) }
    }
}
