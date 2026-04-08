package com.loopers.interfaces.api.queue

import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.application.queue.QueueAdmissionFacade
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.task.scheduling.enabled=false"],
)
class QueueV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val queueAdmissionFacade: QueueAdmissionFacade,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val QUEUE_ENTER = "/api/v1/queue/enter"
        private const val QUEUE_POSITION = "/api/v1/queue/position"
        private const val TEST_PASSWORD = "Password123!"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun createUser(loginId: String) = userJpaRepository.save(
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = passwordEncryptor.encrypt(TEST_PASSWORD),
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("$loginId@example.com"),
        ),
    )

    private fun authHeaders(loginId: String) = HttpHeaders().apply {
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", TEST_PASSWORD)
    }

    private fun queueResponseType() = object : ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>>() {}

    @DisplayName("대기열 API")
    @Nested
    inner class QueueApis {

        @Test
        fun `대기열에 처음 진입하면 1번 순번과 대기 인원 1명이 반환된다`() {
            createUser("queueuser1")

            val response = testRestTemplate.exchange(
                QUEUE_ENTER,
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders("queueuser1")),
                queueResponseType(),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.position).isEqualTo(1L)
            assertThat(response.body?.data?.waitingCount).isEqualTo(1L)
            assertThat(response.body?.data?.entered).isTrue()
            assertThat(response.body?.data?.status).isEqualTo("WAITING")
            assertThat(response.body?.data?.estimatedWaitSeconds).isEqualTo(1L)
            assertThat(response.body?.data?.queueToken).isNull()
            assertThat(response.body?.data?.retryAfterSeconds).isEqualTo(1L)
            assertThat(response.body?.data?.recommendedPollIntervalSeconds).isEqualTo(1L)
        }

        @Test
        fun `이미 진입한 유저가 다시 진입 요청하면 기존 순번을 유지한다`() {
            createUser("queueuser2")

            val firstResponse = testRestTemplate.exchange(
                QUEUE_ENTER,
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders("queueuser2")),
                queueResponseType(),
            )
            val secondResponse = testRestTemplate.exchange(
                QUEUE_ENTER,
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders("queueuser2")),
                queueResponseType(),
            )

            assertThat(firstResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(secondResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(firstResponse.body?.data?.position).isEqualTo(1L)
            assertThat(secondResponse.body?.data?.position).isEqualTo(1L)
            assertThat(secondResponse.body?.data?.waitingCount).isEqualTo(1L)
            assertThat(secondResponse.body?.data?.entered).isFalse()
            assertThat(secondResponse.body?.data?.estimatedWaitSeconds).isEqualTo(1L)
            assertThat(secondResponse.body?.data?.queueToken).isNull()
            assertThat(secondResponse.body?.data?.retryAfterSeconds).isEqualTo(1L)
            assertThat(secondResponse.body?.data?.recommendedPollIntervalSeconds).isEqualTo(1L)
        }

        @Test
        fun `현재 순번 조회 시 전체 대기 인원과 자신의 순번을 반환한다`() {
            createUser("queueuser3")
            createUser("queueuser4")

            testRestTemplate.exchange(
                QUEUE_ENTER,
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders("queueuser3")),
                queueResponseType(),
            )
            testRestTemplate.exchange(
                QUEUE_ENTER,
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders("queueuser4")),
                queueResponseType(),
            )

            val response = testRestTemplate.exchange(
                QUEUE_POSITION,
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders("queueuser4")),
                queueResponseType(),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.position).isEqualTo(2L)
            assertThat(response.body?.data?.waitingCount).isEqualTo(2L)
            assertThat(response.body?.data?.status).isEqualTo("WAITING")
            assertThat(response.body?.data?.estimatedWaitSeconds).isEqualTo(1L)
            assertThat(response.body?.data?.queueToken).isNull()
            assertThat(response.body?.data?.retryAfterSeconds).isEqualTo(1L)
            assertThat(response.body?.data?.recommendedPollIntervalSeconds).isEqualTo(1L)
        }

        @Test
        fun `배치 크기를 넘는 순번은 다음 배치 기준 ETA를 초 단위로 반환한다`() {
            (1..21).forEach { index ->
                val loginId = "queueeta$index"
                createUser(loginId)
                testRestTemplate.exchange(
                    QUEUE_ENTER,
                    HttpMethod.POST,
                    HttpEntity<Any>(authHeaders(loginId)),
                    queueResponseType(),
                )
            }

            val response = testRestTemplate.exchange(
                QUEUE_POSITION,
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders("queueeta21")),
                queueResponseType(),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.position).isEqualTo(21L)
            assertThat(response.body?.data?.estimatedWaitSeconds).isEqualTo(2L)
            assertThat(response.body?.data?.queueToken).isNull()
            assertThat(response.body?.data?.retryAfterSeconds).isEqualTo(2L)
            assertThat(response.body?.data?.recommendedPollIntervalSeconds).isEqualTo(2L)
        }

        @Test
        fun `입장 허용 상태가 되면 순번 조회 응답에 주문 토큰이 포함된다`() {
            createUser("queueallowed1")
            testRestTemplate.exchange(
                QUEUE_ENTER,
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders("queueallowed1")),
                queueResponseType(),
            )
            val admitted = queueAdmissionFacade.admitWaitingUsers(limit = 1)

            val response = testRestTemplate.exchange(
                QUEUE_POSITION,
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders("queueallowed1")),
                queueResponseType(),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.status).isEqualTo("ALLOWED")
            assertThat(response.body?.data?.position).isEqualTo(0L)
            assertThat(response.body?.data?.estimatedWaitSeconds).isEqualTo(0L)
            assertThat(response.body?.data?.queueToken).isEqualTo(admitted.single().token)
            assertThat(response.body?.data?.retryAfterSeconds).isEqualTo(0L)
            assertThat(response.body?.data?.recommendedPollIntervalSeconds).isEqualTo(1L)
        }
    }
}
