package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentRecoveryFacade
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentRecoveryScheduler(
    private val paymentRecoveryFacade: PaymentRecoveryFacade,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 10_000)
    fun recoverPendingPayments() {
        log.info("PENDING 결제 자동 복구 스케줄러 실행")
        paymentRecoveryFacade.recoverPendingPayments(olderThanSeconds = 30)
    }
}
