package com.stock.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.domain.model.Product;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCacheService Unit Tests")
class ProductCacheServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductCacheService productCacheService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id("product-1")
                .sku("SKU-001")
                .name("Test Product")
                .quantity(100)
                .reservedQuantity(10)
                .price(29.99)
                .build();
    }

    @Nested
    @DisplayName("GetCachedProduct Tests")
    class GetCachedProductTests {

        @Test
        @DisplayName("Should return product on cache hit")
        void shouldReturnProductOnCacheHit() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("product:product-1")).thenReturn(Mono.just(testProduct));
            when(objectMapper.convertValue(testProduct, Product.class)).thenReturn(testProduct);

            StepVerifier.create(productCacheService.getCachedProduct("product-1"))
                    .assertNext(product -> {
                        assertThat(product.getId()).isEqualTo("product-1");
                        assertThat(product.getName()).isEqualTo("Test Product");
                        assertThat(product.getQuantity()).isEqualTo(100);
                    })
                    .verifyComplete();

            verify(valueOperations).get("product:product-1");
        }

        @Test
        @DisplayName("Should return empty Mono on cache miss")
        void shouldReturnEmptyOnCacheMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("product:product-1")).thenReturn(Mono.empty());

            StepVerifier.create(productCacheService.getCachedProduct("product-1"))
                    .verifyComplete();

            verify(valueOperations).get("product:product-1");
            verify(objectMapper, never()).convertValue(any(), eq(Product.class));
        }
    }

    @Nested
    @DisplayName("CacheProduct Tests")
    class CacheProductTests {

        @Test
        @DisplayName("Should cache product with TTL and return the product")
        void shouldCacheProductWithTtlAndReturn() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(eq("product:product-1"), eq(testProduct), eq(Duration.ofMinutes(5))))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(productCacheService.cacheProduct(testProduct))
                    .assertNext(product -> {
                        assertThat(product.getId()).isEqualTo("product-1");
                        assertThat(product.getName()).isEqualTo("Test Product");
                    })
                    .verifyComplete();

            verify(valueOperations).set("product:product-1", testProduct, Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("Should use 5-minute TTL for product cache")
        void shouldUseFiveMinuteTtl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), any(), any(Duration.class)))
                    .thenReturn(Mono.just(true));

            productCacheService.cacheProduct(testProduct).block();

            verify(valueOperations).set(anyString(), any(), eq(Duration.ofMinutes(5)));
        }
    }

    @Nested
    @DisplayName("EvictProduct Tests")
    class EvictProductTests {

        @Test
        @DisplayName("Should delete product key and all-products key")
        void shouldDeleteProductAndAllProductsKeys() {
            when(redisTemplate.delete("product:product-1")).thenReturn(Mono.just(1L));
            when(redisTemplate.delete("products:all")).thenReturn(Mono.just(1L));

            StepVerifier.create(productCacheService.evictProduct("product-1"))
                    .verifyComplete();

            verify(redisTemplate).delete("product:product-1");
            verify(redisTemplate).delete("products:all");
        }

        @Test
        @DisplayName("Should complete even if keys dont exist (delete returns 0)")
        void shouldCompleteEvenIfKeysNotExist() {
            when(redisTemplate.delete("product:nonexistent")).thenReturn(Mono.just(0L));
            when(redisTemplate.delete("products:all")).thenReturn(Mono.just(0L));

            StepVerifier.create(productCacheService.evictProduct("nonexistent"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("EvictAllProducts Tests")
    class EvictAllProductsTests {

        @Test
        @DisplayName("Should delete all-products key")
        void shouldDeleteAllProductsKey() {
            when(redisTemplate.delete("products:all")).thenReturn(Mono.just(1L));

            StepVerifier.create(productCacheService.evictAllProducts())
                    .verifyComplete();

            verify(redisTemplate).delete("products:all");
        }

        @Test
        @DisplayName("Should complete even if all-products key does not exist")
        void shouldCompleteEvenIfKeyNotExist() {
            when(redisTemplate.delete("products:all")).thenReturn(Mono.just(0L));

            StepVerifier.create(productCacheService.evictAllProducts())
                    .verifyComplete();
        }
    }
}
