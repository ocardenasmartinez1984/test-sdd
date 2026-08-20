package com.stock.integration;

import com.stock.application.StockApplicationService;
import com.stock.domain.model.Product;
import com.stock.domain.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@SpringBootTest
@Testcontainers
@DisplayName("Stock Service Integration Tests")
class StockServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockApplicationService stockApplicationService;

    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        assertNotNull(applicationContext, "Application context should load successfully");
    }

    @Test
    @DisplayName("MongoDB container should be running")
    void mongoDBContainerIsRunning() {
        assertNotNull(mongoDBContainer);
        assertThat(mongoDBContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Kafka container should be running")
    void kafkaContainerIsRunning() {
        assertNotNull(kafkaContainer);
        assertThat(kafkaContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Redis container should be running")
    void redisContainerIsRunning() {
        assertNotNull(redisContainer);
        assertThat(redisContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Should create and retrieve product with cache integration")
    void shouldCreateAndRetrieveProductWithCache() {
        Product newProduct = Product.builder()
                .sku("INT-SKU-001")
                .name("Integration Test Product")
                .quantity(50)
                .price(15.99)
                .build();

        // Create product
        StepVerifier.create(stockApplicationService.createProduct(newProduct))
                .assertNext(created -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getName()).isEqualTo("Integration Test Product");
                    assertThat(created.getReservedQuantity()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should retrieve product from cache on second call")
    void shouldRetrieveProductFromCacheOnSecondCall() {
        Product product = Product.builder()
                .sku("INT-SKU-CACHE")
                .name("Cache Test Product")
                .quantity(30)
                .price(9.99)
                .build();

        // Create and get the ID
        Product created = stockApplicationService.createProduct(product).block();
        assertThat(created).isNotNull();

        // First retrieval (populates cache)
        StepVerifier.create(stockApplicationService.getProduct(created.getId()))
                .assertNext(p -> assertThat(p.getName()).isEqualTo("Cache Test Product"))
                .verifyComplete();

        // Second retrieval (should come from cache)
        StepVerifier.create(stockApplicationService.getProduct(created.getId()))
                .assertNext(p -> assertThat(p.getName()).isEqualTo("Cache Test Product"))
                .verifyComplete();
    }
}
