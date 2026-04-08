package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueTokenService
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest(
    properties = [
        "spring.task.scheduling.enabled=false",
        "app.queue.admission.fixed-delay-millis=2000",
        "app.queue.admission.batch-size=2",
    ],
)
class QueueAdmissionFacadeIntegrationTest @Autowired constructor(
    private val queueFacade: QueueFacade,
    private val queueAdmissionFacade: QueueAdmissionFacade,
    private val orderQueueTokenService: OrderQueueTokenService,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    fun `스케줄러 처리 대상 수만큼 토큰을 발급하고 대기열에서 제거한다`() {
        val firstUser = createUser("queueadmit1")
        val secondUser = createUser("queueadmit2")
        val thirdUser = createUser("queueadmit3")

        queueFacade.enter(firstUser.loginId.value)
        queueFacade.enter(secondUser.loginId.value)
        queueFacade.enter(thirdUser.loginId.value)

        val admitted = queueAdmissionFacade.admitWaitingUsers(limit = 2)

        assertThat(admitted.map { it.userId }).containsExactly(firstUser.id, secondUser.id)
        assertThat(admitted[1].usableAt).isAfter(admitted[0].usableAt)
        assertThat(orderQueueTokenService.hasActiveToken(firstUser.id)).isTrue()
        assertThat(orderQueueTokenService.hasActiveToken(secondUser.id)).isTrue()
        assertThat(orderQueueTokenService.hasActiveToken(thirdUser.id)).isFalse()

        val allowedStatus = queueFacade.getPosition(firstUser.loginId.value)
        val waitingStatus = queueFacade.getPosition(thirdUser.loginId.value)

        assertThat(allowedStatus.status).isEqualTo("ALLOWED")
        assertThat(allowedStatus.position).isEqualTo(0L)
        assertThat(allowedStatus.estimatedWaitSeconds).isEqualTo(0L)
        assertThat(allowedStatus.queueToken).isEqualTo(admitted.first().token)
        assertThat(allowedStatus.retryAfterSeconds).isEqualTo(0L)
        assertThat(waitingStatus.status).isEqualTo("WAITING")
        assertThat(waitingStatus.position).isEqualTo(1L)
        assertThat(waitingStatus.estimatedWaitSeconds).isEqualTo(2L)
        assertThat(waitingStatus.queueToken).isNull()
        assertThat(waitingStatus.retryAfterSeconds).isEqualTo(2L)
    }

    @Test
    fun `토큰 TTL이 지나면 입장 권한이 만료된다`() {
        val user = createUser("queueadmitttl")

        orderQueueTokenService.issueToken(user.id, ttl = Duration.ofMillis(100))
        Thread.sleep(150)

        assertThat(orderQueueTokenService.hasActiveToken(user.id)).isFalse()
    }

    private fun createUser(loginId: String) = userJpaRepository.save(
        UserModel(
            loginId = LoginId(loginId),
            encryptedPassword = passwordEncryptor.encrypt("Password123!"),
            name = Name("홍길동"),
            birthDate = BirthDate("1990-01-01"),
            email = Email("$loginId@example.com"),
        ),
    )
}
