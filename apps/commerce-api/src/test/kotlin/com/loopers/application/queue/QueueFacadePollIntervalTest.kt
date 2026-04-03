package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueEntry
import com.loopers.domain.queue.OrderQueueService
import com.loopers.domain.queue.OrderQueueTokenService
import com.loopers.domain.queue.QueueStatus
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class QueueFacadePollIntervalTest {
    private val userService: UserService = mockk()
    private val orderQueueService: OrderQueueService = mockk()
    private val orderQueueTokenService: OrderQueueTokenService = mockk()
    private val queueAdmissionProperties = QueueAdmissionProperties()
    private val queueFacade = QueueFacade(
        userService = userService,
        orderQueueService = orderQueueService,
        orderQueueTokenService = orderQueueTokenService,
        queueAdmissionProperties = queueAdmissionProperties,
    )

    @Test
    fun `순번 구간에 따라 권장 polling 주기를 다르게 반환한다`() {
        every { userService.getUserByLoginId("polluser") } returns createUserModel()
        every { orderQueueTokenService.getActiveToken(0L) } returns null

        assertThat(getPollInterval(position = 10L)).isEqualTo(1L)
        assertThat(getPollInterval(position = 50L)).isEqualTo(2L)
        assertThat(getPollInterval(position = 300L)).isEqualTo(5L)
        assertThat(getPollInterval(position = 700L)).isEqualTo(10L)
    }

    private fun getPollInterval(position: Long): Long {
        every { orderQueueService.getWaitingQueueEntryOrNull(0L) } returns OrderQueueEntry(
            userId = 0L,
            position = position,
            waitingCount = position,
            enteredAt = Instant.now(),
            status = QueueStatus.WAITING,
        )

        return queueFacade.getPosition("polluser").recommendedPollIntervalSeconds
    }

    private fun createUserModel() = UserModel(
        loginId = LoginId("polluser"),
        encryptedPassword = "encrypted",
        name = Name("홍길동"),
        birthDate = BirthDate("1990-01-01"),
        email = Email("polluser@example.com"),
    )
}
