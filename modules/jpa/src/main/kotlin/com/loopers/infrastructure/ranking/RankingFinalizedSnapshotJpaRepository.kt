package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingFinalizedScope
import com.loopers.domain.ranking.RankingFinalizedSnapshotModel
import com.loopers.domain.ranking.RankingTargetType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RankingFinalizedSnapshotJpaRepository : JpaRepository<RankingFinalizedSnapshotModel, Long> {
    fun findAllByTargetTypeAndSegmentKeyAndScopeAndAsOfDate(
        targetType: RankingTargetType,
        segmentKey: String,
        scope: RankingFinalizedScope,
        asOfDate: LocalDate,
        pageable: Pageable,
    ): Page<RankingFinalizedSnapshotModel>

    fun findByTargetTypeAndSegmentKeyAndScopeAndAsOfDateAndTargetId(
        targetType: RankingTargetType,
        segmentKey: String,
        scope: RankingFinalizedScope,
        asOfDate: LocalDate,
        targetId: Long,
    ): RankingFinalizedSnapshotModel?

    @Query(
        """
        select max(snapshot.asOfDate)
        from RankingFinalizedSnapshotModel snapshot
        where snapshot.targetType = :targetType
          and snapshot.segmentKey = :segmentKey
          and snapshot.scope = :scope
        """,
    )
    fun findLatestAsOfDate(
        @Param("targetType") targetType: RankingTargetType,
        @Param("segmentKey") segmentKey: String,
        @Param("scope") scope: RankingFinalizedScope,
    ): LocalDate?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByTargetTypeAndSegmentKeyAndScopeAndAsOfDate(
        targetType: RankingTargetType,
        segmentKey: String,
        scope: RankingFinalizedScope,
        asOfDate: LocalDate,
    )
}
