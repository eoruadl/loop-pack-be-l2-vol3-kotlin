package com.loopers.integration.ranking

import com.loopers.CommerceApiApplication
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.brand.BrandFacade
import com.loopers.application.payment.PgPaymentPort
import com.loopers.application.payment.PgPaymentRequest
import com.loopers.application.payment.PgPaymentResponse
import com.loopers.application.payment.PgPaymentStatusResponse
import com.loopers.application.product.ProductFacade
import com.loopers.domain.ranking.ProductRankingSnapshotService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.outbox.OutboxEventPublisher
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.infrastructure.ranking.ProductRankingRedisRepository
import com.loopers.infrastructure.ranking.RankingRedisKeys
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.interfaces.api.payment.PaymentV1Dto
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.interfaces.api.ranking.RankingV1Dto
import com.loopers.messaging.catalog.CatalogEventMessage
import com.loopers.messaging.catalog.CatalogEventType
import com.loopers.testcontainers.KafkaTestContainersConfig
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

@SpringBootTest(
    classes = [CommerceApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.task.scheduling.enabled=false",
        "spring.kafka.listener.auto-startup=true",
        "app.queue.admission.enforce-order-token=false",
        "demo-kafka.test.topic-name=demo.internal.topic-v1",
    ],
)
@Import(
    KafkaTestContainersConfig::class,
    RedisTestContainersConfig::class,
    MySqlTestContainersConfig::class,
    RankingPipelineE2ETest.FakePgConfig::class,
)
class RankingPipelineE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandFacade: BrandFacade,
    private val productFacade: ProductFacade,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncryptor: PasswordEncryptor,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val productRankingRedisRepository: ProductRankingRedisRepository,
    private val productRankingSnapshotService: ProductRankingSnapshotService,
    private val kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val TEST_PASSWORD = "Password123!"
        private const val PRODUCTS = "/api/v1/products"
        private const val RANKINGS = "/api/v1/rankings"
        private const val ORDERS = "/api/v1/orders"
        private const val PAYMENTS = "/api/v1/payments"
    }

    @TestConfiguration
    class FakePgConfig {
        @Bean
        @Primary
        fun fakePgPaymentClient(): FakePgPaymentClient = FakePgPaymentClient()
    }

    class FakePgPaymentClient : PgPaymentPort {
        override fun requestPayment(request: PgPaymentRequest): PgPaymentResponse =
            PgPaymentResponse(pgTransactionId = "fake-pg-tx-${request.orderId}")

        override fun getPayment(pgTxId: String, userId: Long): PgPaymentStatusResponse =
            PgPaymentStatusResponse(pgTransactionId = pgTxId, status = "SUCCESS", failureCode = null)

        override fun getPaymentByOrderId(orderId: Long, userId: Long): PgPaymentStatusResponse? =
            PgPaymentStatusResponse(pgTransactionId = "fake-pg-tx-ORDER-$orderId", status = "SUCCESS", failureCode = null)
    }

    @BeforeEach
    fun waitForConsumers() {
        waitUntil {
            kafkaListenerEndpointRegistry.listenerContainers.all { it.isRunning }
        }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    fun `조회 좋아요 결제 이벤트가 발행되어 redis 랭킹과 ranking api에 반영된다`() {
        val users = (1..10).map { createUser("ranker$it") }
        val allProducts = (1..5).flatMap { brandIndex ->
            val brand = brandFacade.createBrand(
                name = "Brand-$brandIndex",
                logoImageUrl = "logo-$brandIndex.png",
                description = "브랜드 설명 $brandIndex",
                zipCode = "1234$brandIndex",
                roadAddress = "서울특별시 중구 테스트길 $brandIndex",
                detailAddress = "${brandIndex}층",
                email = "brand$brandIndex@example.com",
                phoneNumber = "02-0000-000$brandIndex",
                businessNumber = "123-45-6789$brandIndex",
            )
            (1..20).map { productIndex ->
                productFacade.createProduct(
                    brandId = brand.id,
                    name = "Product-$brandIndex-$productIndex",
                    imageUrl = "product-$brandIndex-$productIndex.png",
                    description = "설명",
                    price = 50_000L,
                    quantity = 100L,
                )
            }
        }

        val targets = allProducts.take(10)

        repeat(20) { getProductDetail(targets[4].id) }
        repeat(5) { getProductDetail(targets[3].id) }
        repeat(3) { getProductDetail(targets[0].id) }

        users.take(10).forEach { likeProduct(it.loginId.value, targets[3].id) }
        users.take(5).forEach { likeProduct(it.loginId.value, targets[5].id) }
        users.take(1).forEach { likeProduct(it.loginId.value, targets[0].id) }

        createPaidOrder(users[0].loginId.value, targets[0].id, quantity = 5L)
        createPaidOrder(users[1].loginId.value, targets[1].id, quantity = 4L)
        createPaidOrder(users[2].loginId.value, targets[2].id, quantity = 2L)

        outboxEventPublisher.publishPendingEvents()

        val redisPage = waitUntilValue {
            val page = productRankingRedisRepository.getPageFromRollingKeys(
                RankingRedisKeys.rollingMinuteKeys(ZonedDateTime.now(RankingRedisKeys.ZONE_ID), 60),
                page = 1,
                size = 10,
            )
            if (page.content.size >= 6) page else null
        }

        assertThat(redisPage.content.map { it.targetId }.take(2))
            .containsExactly(targets[0].id, targets[1].id)
        assertThat(redisPage.content.map { it.targetId }.take(6))
            .containsAll(listOf(targets[0].id, targets[1].id, targets[2].id, targets[3].id, targets[4].id, targets[5].id))

        val rankingResponse = waitUntilValue {
            val response = getRankings("realtime")
            val ids = response.body?.data?.items?.map { it.product.id }.orEmpty()
            if (ids.take(2) == listOf(targets[0].id, targets[1].id) && ids.take(6).containsAll(listOf(
                    targets[0].id,
                    targets[1].id,
                    targets[2].id,
                    targets[3].id,
                    targets[4].id,
                    targets[5].id,
                ))
            ) {
                response
            } else {
                null
            }
        }

        assertThat(rankingResponse.body?.data?.items?.map { it.product.id }?.take(6))
            .isEqualTo(redisPage.content.map { it.targetId }.take(6))

        assertThat(getProductDetail(targets[0].id).body?.data?.weeklyRank).isNull()
        assertThat(getProductDetail(targets[0].id).body?.data?.monthlyRank).isNull()
    }

    @Test
    fun `finalize 이후 weekly monthly 랭킹과 상품 상세 rank가 반영된다`() {
        val user = createUser("weeklyranker")
        val brand = brandFacade.createBrand(
            name = "FinalizeBrand",
            logoImageUrl = "finalize-logo.png",
            description = "브랜드 설명",
            zipCode = "12345",
            roadAddress = "서울특별시 강남구 테스트길 1",
            detailAddress = "101호",
            email = "finalize@example.com",
            phoneNumber = "02-1111-1111",
            businessNumber = "123-45-67891",
        )
        val products = (1..3).map {
            productFacade.createProduct(
                brandId = brand.id,
                name = "Finalize-$it",
                imageUrl = "finalize-$it.png",
                description = "설명",
                price = 50_000L,
                quantity = 100L,
            )
        }

        repeat(5) { getProductDetail(products[1].id) }
        likeProduct(user.loginId.value, products[1].id)
        createPaidOrder(user.loginId.value, products[0].id, quantity = 3L)
        outboxEventPublisher.publishPendingEvents()

        waitUntil {
            getRankings("realtime").body?.data?.items?.map { it.product.id }?.take(2) == listOf(products[0].id, products[1].id)
        }

        val now = ZonedDateTime.now(RankingRedisKeys.ZONE_ID)
        materializeHourFor(now)
        productRankingSnapshotService.finalizePreviousDay(now.plusDays(1).with(LocalTime.of(0, 10)))

        val weekly = getRankings("weekly")
        val monthly = getRankings("monthly")
        assertThat(weekly.body?.data?.items?.map { it.product.id }?.take(2))
            .containsExactly(products[0].id, products[1].id)
        assertThat(monthly.body?.data?.items?.map { it.product.id }?.take(2))
            .containsExactly(products[0].id, products[1].id)

        val detail = getProductDetail(products[0].id)
        assertThat(detail.body?.data?.weeklyRank).isEqualTo(1L)
        assertThat(detail.body?.data?.monthlyRank).isEqualTo(1L)

        val asOfDate = now.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val dayFixed = getRankings("day-fixed", asOfDate)
        assertThat(dayFixed.body?.data?.items?.map { it.product.id }?.first()).isEqualTo(products[0].id)
    }

    @Test
    fun `주문 1건이 좋아요 3건보다 높은 랭킹을 가진다`() {
        val buyer = createUser("weightbuyer")
        val likeUsers = listOf(
            createUser("weightlike1"),
            createUser("weightlike2"),
            createUser("weightlike3"),
        )
        val brand = brandFacade.createBrand(
            name = "WeightBrand",
            logoImageUrl = "weight-logo.png",
            description = "브랜드 설명",
            zipCode = "99999",
            roadAddress = "서울특별시 서초구 테스트길 9",
            detailAddress = "909호",
            email = "weight@example.com",
            phoneNumber = "02-9999-9999",
            businessNumber = "123-45-67893",
        )
        val orderedProduct = productFacade.createProduct(
            brandId = brand.id,
            name = "Weight-Order",
            imageUrl = "weight-order.png",
            description = "설명",
            price = 50_000L,
            quantity = 100L,
        )
        val likedProduct = productFacade.createProduct(
            brandId = brand.id,
            name = "Weight-Like",
            imageUrl = "weight-like.png",
            description = "설명",
            price = 50_000L,
            quantity = 100L,
        )

        likeUsers.forEach { likeProduct(it.loginId.value, likedProduct.id) }
        createPaidOrder(buyer.loginId.value, orderedProduct.id, quantity = 1L)
        outboxEventPublisher.publishPendingEvents()

        val redisRanking = waitUntilValue {
            val page = productRankingRedisRepository.getPageFromRollingKeys(
                RankingRedisKeys.rollingMinuteKeys(ZonedDateTime.now(RankingRedisKeys.ZONE_ID), 60),
                page = 1,
                size = 10,
            )
            if (page.content.map { it.targetId }.containsAll(listOf(orderedProduct.id, likedProduct.id))) page else null
        }

        assertThat(redisRanking.content.map { it.targetId }.take(2))
            .containsExactly(orderedProduct.id, likedProduct.id)

        val apiRanking = waitUntilValue {
            val response = getRankings("realtime")
            val ids = response.body?.data?.items?.map { it.product.id }.orEmpty()
            if (ids.containsAll(listOf(orderedProduct.id, likedProduct.id))) response else null
        }

        assertThat(apiRanking.body?.data?.items?.map { it.product.id }?.take(2))
            .containsExactly(orderedProduct.id, likedProduct.id)
    }

    @Test
    fun `일자가 변경되어도 이전 날짜의 day-fixed 랭킹을 조회할 수 있다`() {
        val brand = brandFacade.createBrand(
            name = "HistoryBrand",
            logoImageUrl = "history-logo.png",
            description = "브랜드 설명",
            zipCode = "11111",
            roadAddress = "서울특별시 송파구 테스트길 11",
            detailAddress = "1101호",
            email = "history@example.com",
            phoneNumber = "02-1111-2222",
            businessNumber = "123-45-67894",
        )
        val previousDayWinner = productFacade.createProduct(
            brandId = brand.id,
            name = "History-Winner",
            imageUrl = "history-winner.png",
            description = "설명",
            price = 50_000L,
            quantity = 100L,
        )
        val currentDayWinner = productFacade.createProduct(
            brandId = brand.id,
            name = "Today-Winner",
            imageUrl = "today-winner.png",
            description = "설명",
            price = 50_000L,
            quantity = 100L,
        )

        val now = ZonedDateTime.now(RankingRedisKeys.ZONE_ID).withSecond(0).withNano(0)
        val previousDayTime = now.minusDays(1).withHour(15).withMinute(10)

        repeat(12) {
            publishCatalogEvent(
                productId = previousDayWinner.id,
                eventType = CatalogEventType.PRODUCT_VIEWED,
                occurredAt = previousDayTime.plusMinutes(it.toLong()),
            )
        }

        materializeHourFor(previousDayTime)
        productRankingSnapshotService.finalizePreviousDay(previousDayTime.plusDays(1).with(LocalTime.of(0, 10)))

        repeat(20) {
            getProductDetail(currentDayWinner.id)
        }
        outboxEventPublisher.publishPendingEvents()

        waitUntil {
            getRankings("realtime").body?.data?.items?.map { it.product.id }?.contains(currentDayWinner.id) == true
        }

        val previousDate = previousDayTime.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val previousDayRanking = getRankings("day-fixed", previousDate)

        assertThat(previousDayRanking.body?.data?.items?.map { it.product.id }?.first())
            .isEqualTo(previousDayWinner.id)
        assertThat(previousDayRanking.body?.data?.items?.map { it.product.id })
            .doesNotContain(currentDayWinner.id)
    }

    @Test
    fun `realtime daily day-fixed는 시간대에 따라 다른 랭킹 결과를 보여준다`() {
        val brand = brandFacade.createBrand(
            name = "CompareBrand",
            logoImageUrl = "compare-logo.png",
            description = "브랜드 설명",
            zipCode = "54321",
            roadAddress = "서울특별시 성동구 테스트길 2",
            detailAddress = "202호",
            email = "compare@example.com",
            phoneNumber = "02-2222-2222",
            businessNumber = "123-45-67892",
        )
        val products = (1..3).map {
            productFacade.createProduct(
                brandId = brand.id,
                name = "Compare-$it",
                imageUrl = "compare-$it.png",
                description = "설명",
                price = 50_000L,
                quantity = 100L,
            )
        }

        val now = ZonedDateTime.now(RankingRedisKeys.ZONE_ID).withSecond(0).withNano(0)
        val oldWindowTime = now.minusHours(2).withMinute(5).withSecond(0).withNano(0)
        repeat(80) {
            publishCatalogEvent(products[1].id, CatalogEventType.PRODUCT_VIEWED, oldWindowTime.plusMinutes((it % 20).toLong()))
        }
        repeat(15) {
            publishCatalogEvent(products[2].id, CatalogEventType.PRODUCT_VIEWED, now.minusMinutes(10).plusMinutes(it.toLong()))
        }
        createUser("compareorderer")
        createPaidOrder("compareorderer", products[0].id, quantity = 1L)
        outboxEventPublisher.publishPendingEvents()

        waitUntil {
            val realtimeIds = getRankings("realtime").body?.data?.items?.map { it.product.id }.orEmpty()
            val dailyIds = getRankings("daily").body?.data?.items?.map { it.product.id }.orEmpty()
            realtimeIds.containsAll(listOf(products[0].id, products[2].id)) &&
                dailyIds.containsAll(listOf(products[0].id, products[1].id, products[2].id))
        }

        val realtime = getRankings("realtime")
        val daily = getRankings("daily")

        assertThat(realtime.body?.data?.items?.map { it.product.id }?.take(2))
            .containsExactly(products[0].id, products[2].id)
        assertThat(daily.body?.data?.items?.map { it.product.id }?.take(3))
            .containsExactly(products[1].id, products[0].id, products[2].id)

        materializeHourFor(oldWindowTime)
        materializeHourFor(now)
        productRankingSnapshotService.finalizePreviousDay(now.plusDays(1).with(LocalTime.of(0, 10)))

        val asOfDate = now.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val dayFixed = getRankings("day-fixed", asOfDate)

        assertThat(dayFixed.body?.data?.items?.map { it.product.id }?.take(3))
            .containsExactly(products[1].id, products[0].id, products[2].id)
    }

    private fun createUser(loginId: String): UserModel =
        userJpaRepository.save(
            UserModel(
                loginId = LoginId(loginId),
                encryptedPassword = passwordEncryptor.encrypt(TEST_PASSWORD),
                name = Name("홍길동"),
                birthDate = BirthDate("1990-01-01"),
                email = Email("$loginId@example.com"),
            ),
        )

    private fun authHeaders(loginId: String) = HttpHeaders().apply {
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", TEST_PASSWORD)
    }

    private fun getProductDetail(productId: Long) =
        testRestTemplate.exchange(
            "$PRODUCTS/$productId",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {},
        )

    private fun likeProduct(loginId: String, productId: Long) {
        testRestTemplate.exchange(
            "$PRODUCTS/$productId/likes",
            HttpMethod.POST,
            HttpEntity<Any>(Unit, authHeaders(loginId)),
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any>>>() {},
        )
    }

    private fun createPaidOrder(
        loginId: String,
        productId: Long,
        quantity: Long,
    ) {
        val orderRequest = OrderV1Dto.CreateOrderRequest(
            items = listOf(OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId = productId, quantity = quantity)),
            cardType = "SAMSUNG",
            cardNo = "1234567890123456",
        )
        val orderResponse = testRestTemplate.exchange(
            ORDERS,
            HttpMethod.POST,
            HttpEntity(orderRequest, authHeaders(loginId)),
            object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
        )
        assertThat(orderResponse.body?.data?.paymentId).isNotNull()

        val payment = paymentJpaRepository.findById(orderResponse.body!!.data!!.paymentId!!).orElseThrow()
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)

        val callbackRequest = PaymentV1Dto.PgCallbackRequest(
            transactionKey = payment.pgTxId!!.value,
            orderId = "ORDER-${payment.orderId}",
            cardType = "SAMSUNG",
            cardNo = "1234567890123456",
            amount = quantity * 50_000L,
            status = "SUCCESS",
            reason = "정상 승인되었습니다.",
        )
        testRestTemplate.exchange(
            "$PAYMENTS/callback",
            HttpMethod.POST,
            HttpEntity(callbackRequest),
            object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
        )
    }

    private fun getRankings(type: String, date: String? = null) =
        testRestTemplate.exchange(
            buildString {
                append("$RANKINGS?type=$type&page=1&size=10")
                if (date != null) append("&date=$date")
            },
            HttpMethod.GET,
            HttpEntity.EMPTY,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {},
        )

    private fun publishCatalogEvent(
        productId: Long,
        eventType: CatalogEventType,
        occurredAt: ZonedDateTime,
    ) {
        val event = CatalogEventMessage(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            productId = productId,
            actorLoginId = null,
            occurredAt = occurredAt,
        )
        kafkaTemplate.send(
            "catalog-events",
            productId.toString(),
            objectMapper.readTree(objectMapper.writeValueAsString(event)),
        ).get()
    }

    private fun materializeHourFor(eventTime: ZonedDateTime) {
        productRankingSnapshotService.materializePreviousHour(
            eventTime.withZoneSameInstant(RankingRedisKeys.ZONE_ID)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .plusHours(1),
        )
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(40) {
            if (condition()) return
            Thread.sleep(500)
        }
        error("조건이 만족되지 않았습니다.")
    }

    private fun <T> waitUntilValue(block: () -> T?): T {
        repeat(40) {
            block()?.let { return it }
            Thread.sleep(500)
        }
        error("조건이 만족되지 않았습니다.")
    }
}
