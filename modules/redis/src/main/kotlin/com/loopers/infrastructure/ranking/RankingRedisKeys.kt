package com.loopers.infrastructure.ranking

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

object RankingRedisKeys {
    const val VERSION = "v1"
    const val TARGET = "product"
    const val SEGMENT = "all"
    val ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")

    private val minuteFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val hourFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH")
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun minuteBucket(at: ZonedDateTime): String =
        "ranking:$VERSION:$TARGET:$SEGMENT:bucket:1m:${at.withZoneSameInstant(ZONE_ID).format(minuteFormatter)}"

    fun hourBucket(at: ZonedDateTime): String =
        "ranking:$VERSION:$TARGET:$SEGMENT:bucket:1h:${at.withZoneSameInstant(ZONE_ID).format(hourFormatter)}"

    fun dayBucket(date: LocalDate): String =
        "ranking:$VERSION:$TARGET:$SEGMENT:bucket:1d:${date.format(dayFormatter)}"

    fun weeklyView(asOfDate: LocalDate): String =
        "ranking:$VERSION:$TARGET:$SEGMENT:view:7d:${asOfDate.format(dayFormatter)}"

    fun monthlyView(asOfDate: LocalDate): String =
        "ranking:$VERSION:$TARGET:$SEGMENT:view:30d:${asOfDate.format(dayFormatter)}"

    fun rollingMinuteKeys(
        now: ZonedDateTime,
        minutes: Long,
    ): List<String> {
        val normalized = now.withZoneSameInstant(ZONE_ID).truncatedTo(ChronoUnit.MINUTES)
        return (minutes - 1 downTo 0).map { offset ->
            minuteBucket(normalized.minusMinutes(offset))
        }
    }
}
