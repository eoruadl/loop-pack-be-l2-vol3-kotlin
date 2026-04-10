package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingCheckpointScope
import com.loopers.domain.ranking.RankingCheckpointSnapshotModel
import com.loopers.domain.ranking.RankingTargetType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface RankingCheckpointSnapshotJpaRepository : JpaRepository<RankingCheckpointSnapshotModel, Long> {
    fun findAllByTargetTypeAndSegmentKeyAndScopeAndCapturedAt(
        targetType: RankingTargetType,
        segmentKey: String,
        scope: RankingCheckpointScope,
        capturedAt: ZonedDateTime,
        pageable: Pageable,
    ): Page<RankingCheckpointSnapshotModel>

    fun findByTargetTypeAndSegmentKeyAndScopeAndCapturedAtAndTargetId(
        targetType: RankingTargetType,
        segmentKey: String,
        scope: RankingCheckpointScope,
        capturedAt: ZonedDateTime,
        targetId: Long,
    ): RankingCheckpointSnapshotModel?

    @Query(
        """
        select max(snapshot.capturedAt)
        from RankingCheckpointSnapshotModel snapshot
        where snapshot.targetType = :targetType
          and snapshot.segmentKey = :segmentKey
          and snapshot.scope = :scope
        """,
    )
    fun findLatestCapturedAt(
        @Param("targetType") targetType: RankingTargetType,
        @Param("segmentKey") segmentKey: String,
        @Param("scope") scope: RankingCheckpointScope,
    ): ZonedDateTime?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByTargetTypeAndSegmentKeyAndScopeAndCapturedAt(
        targetType: RankingTargetType,
        segmentKey: String,
        scope: RankingCheckpointScope,
        capturedAt: ZonedDateTime,
    )
}
