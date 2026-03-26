package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class CardNo(val value: String) {
    init {
        if (value.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 공백일 수 없습니다.")
    }
}
