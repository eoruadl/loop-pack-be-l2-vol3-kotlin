package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.ProductRankingFacade
import com.loopers.application.ranking.RankingType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val productRankingFacade: ProductRankingFacade,
) : RankingV1ApiSpec {
    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    @GetMapping
    override fun getRankings(
        @RequestParam type: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) date: String?,
    ): ApiResponse<RankingV1Dto.RankingPageResponse> =
        productRankingFacade.getRankings(
            type = parseType(type),
            page = page,
            size = size,
            date = date?.let(::parseDate),
        ).let(RankingV1Dto.RankingPageResponse::from)
            .let { ApiResponse.success(it) }

    private fun parseDate(raw: String): LocalDate =
        try {
            LocalDate.parse(raw, DATE_FORMATTER)
        } catch (_: DateTimeParseException) {
            throw CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.")
        }

    private fun parseType(raw: String): RankingType =
        runCatching { RankingType.valueOf(raw.replace('-', '_').uppercase()) }
            .getOrElse {
                throw CoreException(ErrorType.BAD_REQUEST, "type은 realtime, daily, weekly, monthly, day-fixed 중 하나여야 합니다.")
            }
}
