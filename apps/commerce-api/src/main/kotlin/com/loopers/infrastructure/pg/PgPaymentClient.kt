package com.loopers.infrastructure.pg

import com.loopers.application.payment.PgFailureCode
import com.loopers.application.payment.PgPaymentFailException
import com.loopers.application.payment.PgPaymentPort
import com.loopers.application.payment.PgPaymentRequest
import com.loopers.application.payment.PgPaymentResponse
import com.loopers.application.payment.PgPaymentStatusResponse
import com.loopers.application.payment.PgPaymentTimeoutException
import com.loopers.application.payment.parseFailureCode
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate

@Component
class PgPaymentClient(
    @Qualifier("pgRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${pg.base-url:http://localhost:8082}") private val pgBaseUrl: String,
) : PgPaymentPort {

    @CircuitBreaker(name = "pg-payment", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pg-payment", fallbackMethod = "requestPaymentFallback")
    override fun requestPayment(request: PgPaymentRequest): PgPaymentResponse {
        try {
            val headers = HttpHeaders().apply { set("X-USER-ID", request.userId.toString()) }
            val body = PgApiRequest(
                orderId = request.orderId,
                cardType = request.cardType,
                cardNo = request.cardNo,
                amount = request.amount,
                callbackUrl = request.callbackUrl,
            )
            val responseType = object : ParameterizedTypeReference<PgApiResponse<PgTransactionResponse>>() {}
            val data = restTemplate.exchange(
                "$pgBaseUrl/api/v1/payments",
                HttpMethod.POST,
                HttpEntity(body, headers),
                responseType,
            ).body?.data ?: throw PgPaymentFailException("PG 응답이 없습니다.")
            return PgPaymentResponse(pgTransactionId = data.transactionKey)
        } catch (e: RestClientResponseException) {
            throw PgPaymentFailException("PG 결제 요청 실패: ${e.statusCode}", e)
        } catch (e: ResourceAccessException) {
            throw PgPaymentTimeoutException("PG 결제 요청 타임아웃", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun requestPaymentFallback(request: PgPaymentRequest, e: Exception): PgPaymentResponse {
        throw PgPaymentTimeoutException("Circuit breaker open — PG 결제 요청 불가", e)
    }

    @CircuitBreaker(name = "pg-payment", fallbackMethod = "getPaymentFallback")
    @Retry(name = "pg-payment", fallbackMethod = "getPaymentFallback")
    override fun getPayment(pgTxId: String, userId: Long): PgPaymentStatusResponse {
        try {
            val headers = HttpHeaders().apply { set("X-USER-ID", userId.toString()) }
            val responseType = object : ParameterizedTypeReference<PgApiResponse<PgTransactionDetailResponse>>() {}
            val data = restTemplate.exchange(
                "$pgBaseUrl/api/v1/payments/$pgTxId",
                HttpMethod.GET,
                HttpEntity(null, headers),
                responseType,
            ).body?.data ?: throw PgPaymentFailException("PG 응답이 없습니다.")
            return PgPaymentStatusResponse(
                pgTransactionId = data.transactionKey,
                status = data.status,
                failureCode = if (data.status == "FAILED") parseFailureCode(data.reason) else null,
            )
        } catch (e: RestClientResponseException) {
            throw PgPaymentFailException("PG 결제 상태 조회 실패: ${e.statusCode}", e)
        } catch (e: ResourceAccessException) {
            throw PgPaymentTimeoutException("PG 결제 상태 조회 타임아웃", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getPaymentFallback(pgTxId: String, userId: Long, e: Exception): PgPaymentStatusResponse {
        throw PgPaymentTimeoutException("Circuit breaker open — PG 상태 조회 불가", e)
    }

    @CircuitBreaker(name = "pg-payment", fallbackMethod = "getPaymentByOrderIdFallback")
    @Retry(name = "pg-payment", fallbackMethod = "getPaymentByOrderIdFallback")
    override fun getPaymentByOrderId(orderId: Long, userId: Long): PgPaymentStatusResponse? {
        try {
            val headers = HttpHeaders().apply { set("X-USER-ID", userId.toString()) }
            val responseType = object : ParameterizedTypeReference<PgApiResponse<PgOrderResponse>>() {}
            val data = restTemplate.exchange(
                "$pgBaseUrl/api/v1/payments?orderId=${formatOrderId(orderId)}",
                HttpMethod.GET,
                HttpEntity(null, headers),
                responseType,
            ).body?.data ?: return null
            val lastTx = data.transactions.lastOrNull() ?: return null
            return PgPaymentStatusResponse(
                pgTransactionId = lastTx.transactionKey,
                status = lastTx.status,
                failureCode = if (lastTx.status == "FAILED") parseFailureCode(lastTx.reason) else null,
            )
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() == 404) return null
            throw PgPaymentFailException("PG 주문 기반 결제 조회 실패: ${e.statusCode}", e)
        } catch (e: ResourceAccessException) {
            throw PgPaymentTimeoutException("PG 주문 기반 결제 조회 타임아웃", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getPaymentByOrderIdFallback(orderId: Long, userId: Long, e: Exception): PgPaymentStatusResponse? {
        throw PgPaymentTimeoutException("Circuit breaker open — PG 주문 기반 조회 불가", e)
    }

    private fun formatOrderId(orderId: Long): String = "ORDER-$orderId"
}

private data class PgApiRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

private data class PgApiResponse<T>(
    val meta: PgMeta,
    val data: T?,
)

private data class PgMeta(
    val result: String,
    val errorCode: String?,
    val message: String?,
)

private data class PgTransactionResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)

private data class PgTransactionDetailResponse(
    val transactionKey: String,
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val status: String,
    val reason: String?,
)

private data class PgOrderResponse(
    val orderId: String,
    val transactions: List<PgTransactionSummary>,
)

private data class PgTransactionSummary(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)
