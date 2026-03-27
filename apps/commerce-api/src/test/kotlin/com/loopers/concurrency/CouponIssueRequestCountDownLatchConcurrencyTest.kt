package com.loopers.concurrency

import com.loopers.application.couponrequest.CouponIssueRequestFacade
import com.loopers.application.couponrequest.CouponIssueRequestProcessingService
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.application.coupon.CouponFacade
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class CouponIssueRequestCountDownLatchConcurrencyTest @Autowired constructor(
    private val couponFacade: CouponFacade,
    private val couponIssueRequestFacade: CouponIssueRequestFacade,
    private val couponIssueRequestProcessingService: CouponIssueRequestProcessingService,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun createUser(loginId: String) = userJpaRepository.save(
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = passwordEncryptor.encrypt("Password123!"),
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("$loginId@example.com"),
        )
    )

    @Test
    fun `선착순 쿠폰 발급 요청을 동시에 처리해도 제한 수량만큼만 성공한다`() {
        val issueLimit = 10L
        val requestCount = 30
        val template = couponFacade.createTemplate(
            name = "선착순 쿠폰",
            type = "FIXED",
            value = 1_000L,
            minOrderAmount = null,
            expiredAt = ZonedDateTime.now().plusDays(1),
            issueLimit = issueLimit,
        )

        val requests = (1..requestCount).map { index ->
            createUser("user$index")
            "user$index" to couponIssueRequestFacade.requestIssue("user$index", template.id).requestId
        }

        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(requestCount)

        requests.forEach { (_, requestId) ->
            executor.submit {
                latch.await()
                couponIssueRequestProcessingService.process(requestId)
            }
        }

        latch.countDown()
        executor.shutdown()
        executor.awaitTermination(20, TimeUnit.SECONDS)

        val results = requests.map { (loginId, requestId) -> couponIssueRequestFacade.getRequest(loginId, requestId) }
        assertThat(results.count { it.status == "ISSUED" }).isEqualTo(issueLimit.toInt())
        assertThat(results.count { it.status == "FAILED" }).isEqualTo(requestCount - issueLimit.toInt())
    }
}
