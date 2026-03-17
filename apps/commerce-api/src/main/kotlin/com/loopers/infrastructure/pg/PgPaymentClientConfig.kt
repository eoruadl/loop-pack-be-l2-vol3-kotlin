package com.loopers.infrastructure.pg

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class PgPaymentClientConfig {

    @Bean(name = ["pgRestTemplate"])
    fun pgRestTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3_000)
            setReadTimeout(3_000)
        }
        return RestTemplate(factory)
    }
}
