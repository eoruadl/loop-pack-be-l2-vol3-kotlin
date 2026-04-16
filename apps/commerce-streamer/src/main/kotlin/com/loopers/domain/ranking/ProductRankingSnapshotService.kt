package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductRankingRedisRepository
import com.loopers.infrastructure.ranking.RankingCheckpointSnapshotJpaRepository
import com.loopers.infrastructure.ranking.RankingFinalizedSnapshotJpaRepository
import com.loopers.infrastructure.ranking.RankingRedisKeys
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Service
class ProductRankingSnapshotService(
    private val productRankingRedisRepository: ProductRankingRedisRepository,
    private val rankingFinalizedSnapshotJpaRepository: RankingFinalizedSnapshotJpaRepository,
    private val rankingCheckpointSnapshotJpaRepository: RankingCheckpointSnapshotJpaRepository,
) {
    companion object {
        private const val SEGMENT_KEY = RankingRedisKeys.SEGMENT
        private val TARGET_TYPE = RankingTargetType.PRODUCT
    }

    fun materializePreviousHour(referenceTime: ZonedDateTime = ZonedDateTime.now(RankingRedisKeys.ZONE_ID)) {
        val previousHour = referenceTime.withZoneSameInstant(RankingRedisKeys.ZONE_ID)
            .truncatedTo(ChronoUnit.HOURS)
            .minusHours(1)
        productRankingRedisRepository.materializeHourBucket(previousHour)
    }

    @Transactional
    fun finalizePreviousDay(referenceTime: ZonedDateTime = ZonedDateTime.now(RankingRedisKeys.ZONE_ID)) {
        val asOfDate = referenceTime.withZoneSameInstant(RankingRedisKeys.ZONE_ID).toLocalDate().minusDays(1)
        productRankingRedisRepository.materializeDayBucket(asOfDate)
        productRankingRedisRepository.materializeWeeklyView(asOfDate)
        productRankingRedisRepository.materializeMonthlyView(asOfDate)

        persistFinalizedSnapshot(RankingFinalizedScope.DAY, asOfDate, RankingRedisKeys.dayBucket(asOfDate))
        persistFinalizedSnapshot(RankingFinalizedScope.WEEKLY, asOfDate, RankingRedisKeys.weeklyView(asOfDate))
        persistFinalizedSnapshot(RankingFinalizedScope.MONTHLY, asOfDate, RankingRedisKeys.monthlyView(asOfDate))
    }

    @Transactional
    fun captureRollingCheckpoint(referenceTime: ZonedDateTime = ZonedDateTime.now(RankingRedisKeys.ZONE_ID)) {
        val normalized = referenceTime.withZoneSameInstant(RankingRedisKeys.ZONE_ID).truncatedTo(ChronoUnit.MINUTES)
        val capturedAt = normalized.withMinute((normalized.minute / 5) * 5)
        persistCheckpointSnapshot(
            RankingCheckpointScope.REALTIME,
            capturedAt,
            productRankingRedisRepository.getTopFromRollingKeys(
                RankingRedisKeys.rollingMinuteKeys(normalized, 60),
                100,
            ),
        )
        persistCheckpointSnapshot(
            RankingCheckpointScope.DAILY,
            capturedAt,
            productRankingRedisRepository.getTopFromRollingKeys(
                RankingRedisKeys.rollingMinuteKeys(normalized, 1_440),
                100,
            ),
        )
    }

    private fun persistFinalizedSnapshot(
        scope: RankingFinalizedScope,
        asOfDate: LocalDate,
        key: String,
    ) {
        rankingFinalizedSnapshotJpaRepository.deleteByTargetTypeAndSegmentKeyAndScopeAndAsOfDate(
            targetType = TARGET_TYPE,
            segmentKey = SEGMENT_KEY,
            scope = scope,
            asOfDate = asOfDate,
        )

        val items = productRankingRedisRepository.getAllFromKey(key)
        if (items.isEmpty()) return

        rankingFinalizedSnapshotJpaRepository.saveAll(
            items.map { item ->
                RankingFinalizedSnapshotModel(
                    targetType = TARGET_TYPE,
                    segmentKey = SEGMENT_KEY,
                    scope = scope,
                    asOfDate = asOfDate,
                    rankPosition = item.rank,
                    targetId = item.targetId,
                    score = item.score,
                )
            },
        )
    }

    private fun persistCheckpointSnapshot(
        scope: RankingCheckpointScope,
        capturedAt: ZonedDateTime,
        items: List<ProductRankingRedisRepository.RankingScore>,
    ) {
        rankingCheckpointSnapshotJpaRepository.deleteByTargetTypeAndSegmentKeyAndScopeAndCapturedAt(
            targetType = TARGET_TYPE,
            segmentKey = SEGMENT_KEY,
            scope = scope,
            capturedAt = capturedAt,
        )

        if (items.isEmpty()) return

        rankingCheckpointSnapshotJpaRepository.saveAll(
            items.map { item ->
                RankingCheckpointSnapshotModel(
                    targetType = TARGET_TYPE,
                    segmentKey = SEGMENT_KEY,
                    scope = scope,
                    capturedAt = capturedAt,
                    rankPosition = item.rank,
                    targetId = item.targetId,
                    score = item.score,
                )
            },
        )
    }
}
