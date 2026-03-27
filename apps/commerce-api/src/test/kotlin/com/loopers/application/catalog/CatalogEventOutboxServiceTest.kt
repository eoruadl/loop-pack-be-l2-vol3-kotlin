package com.loopers.application.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventService
import com.loopers.messaging.catalog.CatalogEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CatalogEventOutboxServiceTest {

    private val outboxEventService: OutboxEventService = mock()
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
    private val service = CatalogEventOutboxService(outboxEventService, objectMapper, "catalog-events")

    @Test
    fun `카탈로그 이벤트를 아웃박스에 저장한다`() {
        whenever(outboxEventService.save(any())).thenAnswer {
            val command = it.getArgument<OutboxEventService.SaveOutboxEventCommand>(0)
            OutboxEventModel(
                eventId = command.eventId,
                topic = command.topic,
                eventKey = command.eventKey,
                eventType = command.eventType,
                payload = command.payload,
            )
        }

        val saved = service.enqueue(
            CatalogEventOutboxCommand(
                eventType = CatalogEventType.PRODUCT_LIKED,
                productId = 1L,
                actorLoginId = "testuser",
            )
        )

        assertThat(saved.topic).isEqualTo("catalog-events")
        assertThat(saved.eventKey).isEqualTo("1")
        assertThat(saved.eventType).isEqualTo(CatalogEventType.PRODUCT_LIKED.name)
        assertThat(saved.payload).contains("PRODUCT_LIKED")
    }
}
