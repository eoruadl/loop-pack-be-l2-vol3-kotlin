package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CardNoTest {

    @Nested
    inner class Create {

        @Test
        fun `유효한 카드 번호로 생성한다`() {
            assertDoesNotThrow {
                CardNo("1234-5678-9012-3456")
            }
        }

        @Test
        fun `공백 문자열로 생성 시 BAD_REQUEST CoreException이 발생한다`() {
            val exception = assertThrows<CoreException> {
                CardNo("")
            }
            assertEquals(ErrorType.BAD_REQUEST, exception.errorType)
        }

        @Test
        fun `공백만 있는 문자열로 생성 시 BAD_REQUEST CoreException이 발생한다`() {
            val exception = assertThrows<CoreException> {
                CardNo("   ")
            }
            assertEquals(ErrorType.BAD_REQUEST, exception.errorType)
        }

        @Test
        fun `카드 번호 마스킹 시 마지막 4자리만 남긴다`() {
            val masked = CardNo("1234-5678-9012-3456").masked()

            assertEquals("****-****-****-3456", masked)
        }
    }
}
