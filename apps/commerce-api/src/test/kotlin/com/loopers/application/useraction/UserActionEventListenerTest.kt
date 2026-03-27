package com.loopers.application.useraction

import com.loopers.domain.useraction.UserActionLogService
import com.loopers.domain.useraction.UserActionTargetType
import com.loopers.domain.useraction.UserActionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UserActionEventListenerTest {

    private val userActionLogService: UserActionLogService = mock()
    private val listener = UserActionEventListener(userActionLogService)

    @Test
    fun `유저 액션 이벤트를 받으면 로그를 저장한다`() {
        listener.handle(
            UserActionEvent(
                actionType = UserActionType.PRODUCT_DETAIL_VIEW,
                actorLoginId = "testuser",
                targetType = UserActionTargetType.PRODUCT,
                targetId = 1L,
            )
        )

        verify(userActionLogService).record(org.mockito.kotlin.any())
    }

    @Test
    fun `로그 저장 실패가 발생해도 예외를 전파하지 않는다`() {
        doThrow(IllegalStateException("boom")).`when`(userActionLogService).record(org.mockito.kotlin.any())

        assertDoesNotThrow {
            listener.handle(
                UserActionEvent(
                    actionType = UserActionType.ORDER_CREATE,
                    actorLoginId = "testuser",
                    targetType = UserActionTargetType.ORDER,
                    targetId = 1L,
                )
            )
        }
    }
}
