package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class Password(val value: String) {

    init {
        require(value.matches(PASSWORD_PATTERN)) {
            "Password는 8~16자의 영문 대소문자, 숫자, 특수문자로만 구성합니다."
        }

        require(value.isNotBlank()) {
            "Password는 필수 입니다."
        }
    }

    companion object {
        val PASSWORD_PATTERN = """^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]).{8,16}$""".toRegex()
    }

    fun validateNotContainsBirthDate(birthDate: BirthDate) {
        val fullBirthDate = birthDate.value.replace("-", "") // "20020101"
        val shortBirthDate = fullBirthDate.substring(2) // "020101"

        if (value.contains(fullBirthDate) || value.contains(shortBirthDate)) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "비밀번호에 생년월일(8자리 또는 6자리)을 포함할 수 없습니다.",
            )
        }
    }
}
