package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductRankingRedisRepository
import com.loopers.infrastructure.ranking.RankingCheckpointSnapshotJpaRepository
import com.loopers.infrastructure.ranking.RankingFinalizedSnapshotJpaRepository
import com.loopers.infrastructure.ranking.RankingRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZonedDateTime

class ProductRankingSnapshotServiceTest {
    private val productRankingRedisRepository: ProductRankingRedisRepository = mock()
    private val rankingFinalizedSnapshotJpaRepository: RankingFinalizedSnapshotJpaRepository = mock()
    private val rankingCheckpointSnapshotJpaRepository: RankingCheckpointSnapshotJpaRepository = mock()
    private val service = ProductRankingSnapshotService(
        productRankingRedisRepository = productRankingRedisRepository,
        rankingFinalizedSnapshotJpaRepository = rankingFinalizedSnapshotJpaRepository,
        rankingCheckpointSnapshotJpaRepository = rankingCheckpointSnapshotJpaRepository,
    )

    @Test
    fun `finalizePreviousDay는 일간 주간 월간 키를 materialize하고 DB 스냅샷으로 저장한다`() {
        val asOfDate = LocalDate.of(2026, 4, 9)
        whenever(productRankingRedisRepository.getAllFromKey(RankingRedisKeys.dayBucket(asOfDate))).thenReturn(
            listOf(ProductRankingRedisRepository.RankingScore(targetId = 11L, score = 1.2, rank = 1L)),
        )
        whenever(productRankingRedisRepository.getAllFromKey(RankingRedisKeys.weeklyView(asOfDate))).thenReturn(
            listOf(ProductRankingRedisRepository.RankingScore(targetId = 11L, score = 4.2, rank = 1L)),
        )
        whenever(productRankingRedisRepository.getAllFromKey(RankingRedisKeys.monthlyView(asOfDate))).thenReturn(
            listOf(ProductRankingRedisRepository.RankingScore(targetId = 11L, score = 9.2, rank = 1L)),
        )

        service.finalizePreviousDay(ZonedDateTime.of(2026, 4, 10, 0, 10, 0, 0, RankingRedisKeys.ZONE_ID))

        verify(productRankingRedisRepository).materializeDayBucket(asOfDate)
        verify(productRankingRedisRepository).materializeWeeklyView(asOfDate)
        verify(productRankingRedisRepository).materializeMonthlyView(asOfDate)
        verify(rankingFinalizedSnapshotJpaRepository).deleteByTargetTypeAndSegmentKeyAndScopeAndAsOfDate(
            RankingTargetType.PRODUCT,
            RankingRedisKeys.SEGMENT,
            RankingFinalizedScope.DAY,
            asOfDate,
        )

        val captor = argumentCaptor<Iterable<RankingFinalizedSnapshotModel>>()
        verify(rankingFinalizedSnapshotJpaRepository, org.mockito.kotlin.times(3)).saveAll(captor.capture())
        assertThat(captor.allValues.flatten())
            .extracting<Long> { it.targetId }
            .contains(11L)
    }

    @Test
    fun `captureRollingCheckpoint는 realtime과 daily top100을 DB에 저장한다`() {
        val now = ZonedDateTime.of(2026, 4, 10, 12, 7, 0, 0, RankingRedisKeys.ZONE_ID)
        whenever(productRankingRedisRepository.getTopFromRollingKeys(any(), eq(100))).thenReturn(
            listOf(ProductRankingRedisRepository.RankingScore(targetId = 99L, score = 0.1, rank = 1L)),
        )

        service.captureRollingCheckpoint(now)

        val capturedAt = now.withMinute(5).withSecond(0).withNano(0)
        verify(rankingCheckpointSnapshotJpaRepository).deleteByTargetTypeAndSegmentKeyAndScopeAndCapturedAt(
            RankingTargetType.PRODUCT,
            RankingRedisKeys.SEGMENT,
            RankingCheckpointScope.REALTIME,
            capturedAt,
        )
        verify(rankingCheckpointSnapshotJpaRepository).deleteByTargetTypeAndSegmentKeyAndScopeAndCapturedAt(
            RankingTargetType.PRODUCT,
            RankingRedisKeys.SEGMENT,
            RankingCheckpointScope.DAILY,
            capturedAt,
        )

        val captor = argumentCaptor<Iterable<RankingCheckpointSnapshotModel>>()
        verify(rankingCheckpointSnapshotJpaRepository, org.mockito.kotlin.times(2)).saveAll(captor.capture())
        assertThat(captor.allValues.flatten())
            .extracting<Long> { it.targetId }
            .containsOnly(99L)
    }
}
