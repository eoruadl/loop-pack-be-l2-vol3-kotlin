package com.loopers.concurrency

import com.loopers.application.queue.QueueFacade
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@SpringBootTest(properties = ["spring.task.scheduling.enabled=false"])
class QueueFacadeConcurrencyTest @Autowired constructor(
    private val queueFacade: QueueFacade,
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
    fun `여러 유저가 동시에 진입해도 각 유저는 유일한 순번을 가진다`() {
        val totalUsers = 20
        val loginIds = (1..totalUsers).map { index ->
            val loginId = "queueconcurrency$index"
            userJpaRepository.save(
                UserModel(
                    loginId = LoginId(loginId),
                    encryptedPassword = passwordEncryptor.encrypt("Password123!"),
                    name = Name("홍길동"),
                    birthDate = BirthDate("1990-01-01"),
                    email = Email("$loginId@example.com"),
                ),
            )
            loginId
        }

        val executor = Executors.newFixedThreadPool(totalUsers)
        val starter = CompletableFuture<Void>()

        val futures = loginIds.map { loginId ->
            starter.thenApplyAsync({ _: Void? -> queueFacade.enter(loginId) }, executor)
        }

        starter.complete(null)
        val infos = futures.map { it.join() }
        executor.shutdown()
        val latestInfos = loginIds.map { queueFacade.getPosition(it) }

        assertThat(infos.map { it.position }.toSet()).hasSize(totalUsers)
        assertThat(latestInfos.map { it.waitingCount }.toSet()).containsOnly(totalUsers.toLong())
        assertThat(infos.map { it.position }).containsAll((1L..totalUsers.toLong()).toList())
    }
}
