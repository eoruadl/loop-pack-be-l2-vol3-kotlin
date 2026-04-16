package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "tb_ranking_checkpoint_snapshot")
class RankingCheckpointSnapshotModel(
    targetType: RankingTargetType,
    segmentKey: String,
    scope: RankingCheckpointScope,
    capturedAt: ZonedDateTime,
    rankPosition: Long,
    targetId: Long,
    score: Double,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    var targetType: RankingTargetType = targetType
        protected set

    @Column(name = "segment_key", nullable = false, length = 64)
    var segmentKey: String = segmentKey
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    var scope: RankingCheckpointScope = scope
        protected set

    @Column(name = "captured_at", nullable = false)
    var capturedAt: ZonedDateTime = capturedAt
        protected set

    @Column(name = "rank_position", nullable = false)
    var rankPosition: Long = rankPosition
        protected set

    @Column(name = "target_id", nullable = false)
    var targetId: Long = targetId
        protected set

    @Column(name = "score", nullable = false)
    var score: Double = score
        protected set
}
