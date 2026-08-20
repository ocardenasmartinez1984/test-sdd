package com.stock.application;

import com.stock.domain.model.Product;
import com.stock.domain.repository.ProductRepository;
import com.stock.infrastructure.config.ProductCacheService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("StockApplicationService Unit Tests")
class StockApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCacheService productCacheService;

    @InjectMocks
    private StockApplicationService stockApplicationService;

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

        // Default lenient stubs for cache service to avoid NPE
        lenient().when(productCacheService.getCachedProduct(anyString())).thenReturn(Mono.empty());
        lenient().when(productCacheService.cacheProduct(any(Product.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        lenient().when(productCacheService.evictProduct(anyString())).thenReturn(Mono.empty());
        lenient().when(productCacheService.evictAllProducts()).thenReturn(Mono.empty());
        // Default lenient stub for repository to avoid NPE in reactive chain building
        lenient().when(productRepository.findById(anyString())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Exists Tests")
    class ExistsTests {

        @Test
        @DisplayName("Should return true when product exists")
        void shouldReturnTrueWhenProductExists() {
            when(productRepository.existsById("product-1")).thenReturn(Mono.just(true));

            StepVerifier.create(stockApplicationService.exists("product-1"))
                    .assertNext(exists -> assertThat(exists).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when product does not exist")
        void shouldReturnFalseWhenProductNotExists() {
            when(productRepository.existsById("nonexistent")).thenReturn(Mono.just(false));

            StepVerifier.create(stockApplicationService.exists("nonexistent"))
                    .assertNext(exists -> assertThat(exists).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("IsAvailable Tests")
    class IsAvailableTests {

        @Test
        @DisplayName("Should return true when sufficient stock available")
        void shouldReturnTrueWhenSufficientStock() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            // available = 100 - 10 = 90, requesting 50
            StepVerifier.create(stockApplicationService.isAvailable("product-1", 50))
                    .assertNext(available -> assertThat(available).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return true when requesting exact available quantity")
        void shouldReturnTrueWhenRequestingExactAvailable() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            // available = 100 - 10 = 90, requesting 90
            StepVerifier.create(stockApplicationService.isAvailable("product-1", 90))
                    .assertNext(available -> assertThat(available).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when insufficient stock")
        void shouldReturnFalseWhenInsufficientStock() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            // available = 100 - 10 = 90, requesting 100
            StepVerifier.create(stockApplicationService.isAvailable("product-1", 100))
                    .assertNext(available -> assertThat(available).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when product not found")
        void shouldReturnFalseWhenProductNotFound() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.isAvailable("nonexistent", 5))
                    .assertNext(available -> assertThat(available).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("GetAvailableQuantity Tests")
    class GetAvailableQuantityTests {

        @Test
        @DisplayName("Should return correct available quantity")
        void shouldReturnCorrectAvailableQuantity() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            StepVerifier.create(stockApplicationService.getAvailableQuantity("product-1"))
                    .assertNext(quantity -> assertThat(quantity).isEqualTo(90)) // 100 - 10
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return 0 when product not found")
        void shouldReturnZeroWhenProductNotFound() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.getAvailableQuantity("nonexistent"))
                    .assertNext(quantity -> assertThat(quantity).isEqualTo(0))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Reserve Tests")
    class ReserveTests {

        @Test
        @DisplayName("Should reserve stock successfully when available")
        void shouldReserveStockSuccessfully() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(testProduct));
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.reserve("order-1", "product-1", 50))
                    .assertNext(success -> assertThat(success).isTrue())
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }

        @Test
        @DisplayName("Should fail reserve when insufficient stock")
        void shouldFailReserveWhenInsufficientStock() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            // available = 100 - 10 = 90, requesting 100
            StepVerifier.create(stockApplicationService.reserve("order-1", "product-1", 100))
                    .assertNext(success -> assertThat(success).isFalse())
                    .verifyComplete();

            verify(productRepository, never()).save(any(Product.class));
            verify(productCacheService, never()).evictProduct(anyString());
        }

        @Test
        @DisplayName("Should fail reserve when product not found")
        void shouldFailReserveWhenProductNotFound() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.reserve("order-1", "nonexistent", 5))
                    .assertNext(success -> assertThat(success).isFalse())
                    .verifyComplete();

            verify(productCacheService, never()).evictProduct(anyString());
        }

        @Test
        @DisplayName("Should evict cache after successful reservation")
        void shouldEvictCacheAfterSuccessfulReservation() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(testProduct));
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.reserve("order-1", "product-1", 5))
                    .assertNext(success -> assertThat(success).isTrue())
                    .verifyComplete();

            verify(productCacheService).evictProduct("product-1");
        }
    }

    @Nested
    @DisplayName("Release Tests")
    class ReleaseTests {

        @Test
        @DisplayName("Should release reserved stock and evict cache")
        void shouldReleaseReservedStockAndEvictCache() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(testProduct));
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.release("order-1", "product-1", 5))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }

        @Test
        @DisplayName("Should not go below zero on release")
        void shouldNotGoBelowZeroOnRelease() {
            testProduct.setReservedQuantity(3);
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                assertThat(saved.getReservedQuantity()).isGreaterThanOrEqualTo(0);
                return Mono.just(saved);
            });
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.release("order-1", "product-1", 10))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }

        @Test
        @DisplayName("Should complete without error when product not found")
        void shouldCompleteWhenProductNotFound() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.release("order-1", "nonexistent", 5))
                    .verifyComplete();

            verify(productRepository, never()).save(any(Product.class));
            verify(productCacheService, never()).evictProduct(anyString());
        }
    }

    @Nested
    @DisplayName("ConfirmDispatch Tests")
    class ConfirmDispatchTests {

        @Test
        @DisplayName("Should confirm dispatch by reducing quantity and reserved, then evict cache")
        void shouldConfirmDispatchAndEvictCache() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                assertThat(saved.getQuantity()).isEqualTo(95); // 100 - 5
                assertThat(saved.getReservedQuantity()).isEqualTo(5); // 10 - 5
                return Mono.just(saved);
            });
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.confirmDispatch("order-1", "product-1", 5))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }

        @Test
        @DisplayName("Should complete without error when product not found for dispatch")
        void shouldCompleteWhenProductNotFoundForDispatch() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.confirmDispatch("order-1", "nonexistent", 5))
                    .verifyComplete();

            verify(productRepository, never()).save(any(Product.class));
            verify(productCacheService, never()).evictProduct(anyString());
        }
    }

    @Nested
    @DisplayName("GetProduct Tests - Cache-Aside Pattern")
    class GetProductCacheAsideTests {

        @Test
        @DisplayName("Should return product from cache on cache hit")
        void shouldReturnProductFromCacheOnHit() {
            when(productCacheService.getCachedProduct("product-1")).thenReturn(Mono.just(testProduct));

            StepVerifier.create(stockApplicationService.getProduct("product-1"))
                    .assertNext(product -> {
                        assertThat(product.getId()).isEqualTo("product-1");
                        assertThat(product.getName()).isEqualTo("Test Product");
                    })
                    .verifyComplete();

            verify(productCacheService).getCachedProduct("product-1");
        }

        @Test
        @DisplayName("Should fetch from DB and cache on cache miss")
        void shouldFetchFromDbAndCacheOnMiss() {
            when(productCacheService.getCachedProduct("product-1")).thenReturn(Mono.empty());
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productCacheService.cacheProduct(testProduct)).thenReturn(Mono.just(testProduct));

            StepVerifier.create(stockApplicationService.getProduct("product-1"))
                    .assertNext(product -> {
                        assertThat(product.getId()).isEqualTo("product-1");
                        assertThat(product.getName()).isEqualTo("Test Product");
                    })
                    .verifyComplete();

            verify(productCacheService).getCachedProduct("product-1");
            verify(productRepository).findById("product-1");
            verify(productCacheService).cacheProduct(testProduct);
        }

        @Test
        @DisplayName("Should return empty when cache miss and product not in DB")
        void shouldReturnEmptyWhenCacheMissAndNotInDb() {
            when(productCacheService.getCachedProduct("nonexistent")).thenReturn(Mono.empty());
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.getProduct("nonexistent"))
                    .verifyComplete();

            verify(productCacheService).getCachedProduct("nonexistent");
            verify(productRepository).findById("nonexistent");
            verify(productCacheService, never()).cacheProduct(any(Product.class));
        }
    }

    @Nested
    @DisplayName("GetAllProducts Tests")
    class GetAllProductsTests {

        @Test
        @DisplayName("Should get all products from repository")
        void shouldGetAllProducts() {
            when(productRepository.findAll()).thenReturn(Flux.just(testProduct));

            StepVerifier.create(stockApplicationService.getAllProducts())
                    .assertNext(product -> assertThat(product.getId()).isEqualTo("product-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux when no products exist")
        void shouldReturnEmptyWhenNoProducts() {
            when(productRepository.findAll()).thenReturn(Flux.empty());

            StepVerifier.create(stockApplicationService.getAllProducts())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("CreateProduct Tests")
    class CreateProductTests {

        @Test
        @DisplayName("Should create product with default reserved quantity and cache it")
        void shouldCreateProductWithDefaultReservedQuantityAndCache() {
            Product newProduct = Product.builder()
                    .sku("SKU-002")
                    .name("New Product")
                    .quantity(50)
                    .price(19.99)
                    .build();

            Product savedProduct = Product.builder()
                    .id("product-2")
                    .sku("SKU-002")
                    .name("New Product")
                    .quantity(50)
                    .reservedQuantity(0)
                    .price(19.99)
                    .build();

            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(savedProduct));
            when(productCacheService.cacheProduct(savedProduct)).thenReturn(Mono.just(savedProduct));

            StepVerifier.create(stockApplicationService.createProduct(newProduct))
                    .assertNext(product -> {
                        assertThat(product.getReservedQuantity()).isEqualTo(0);
                        assertThat(product.getId()).isEqualTo("product-2");
                    })
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).cacheProduct(savedProduct);
        }

        @Test
        @DisplayName("Should preserve existing reserved quantity when not null")
        void shouldPreserveExistingReservedQuantity() {
            Product newProduct = Product.builder()
                    .sku("SKU-003")
                    .name("Product With Reserved")
                    .quantity(50)
                    .reservedQuantity(5)
                    .price(19.99)
                    .build();

            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(newProduct));
            when(productCacheService.cacheProduct(newProduct)).thenReturn(Mono.just(newProduct));

            StepVerifier.create(stockApplicationService.createProduct(newProduct))
                    .assertNext(product -> assertThat(product.getReservedQuantity()).isEqualTo(5))
                    .verifyComplete();

            verify(productCacheService).cacheProduct(newProduct);
        }
    }

    @Nested
    @DisplayName("UpdateStock Tests")
    class UpdateStockTests {

        @Test
        @DisplayName("Should update stock quantity and evict cache")
        void shouldUpdateStockQuantityAndEvictCache() {
            Product updatedProduct = Product.builder()
                    .id("product-1")
                    .quantity(200)
                    .build();

            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(updatedProduct));
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.updateStock("product-1", 200))
                    .assertNext(product -> assertThat(product.getQuantity()).isEqualTo(200))
                    .verifyComplete();

            verify(productCacheService).evictProduct("product-1");
        }

        @Test
        @DisplayName("Should return empty when updating non-existent product")
        void shouldReturnEmptyWhenProductNotFound() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.updateStock("nonexistent", 100))
                    .verifyComplete();

            verify(productRepository, never()).save(any(Product.class));
            verify(productCacheService, never()).evictProduct(anyString());
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class CircuitBreakerFallbackTests {

        @Test
        @DisplayName("existsFallback should return false")
        void existsFallbackShouldReturnFalse() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("existsFallback", String.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Boolean> result = (Mono<Boolean>) fallback.invoke(stockApplicationService, "product-1", new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .assertNext(value -> assertThat(value).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("isAvailableFallback should return false")
        void isAvailableFallbackShouldReturnFalse() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("isAvailableFallback", String.class, int.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Boolean> result = (Mono<Boolean>) fallback.invoke(stockApplicationService, "product-1", 5, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .assertNext(value -> assertThat(value).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("getAvailableQuantityFallback should return 0")
        void getAvailableQuantityFallbackShouldReturnZero() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("getAvailableQuantityFallback", String.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Integer> result = (Mono<Integer>) fallback.invoke(stockApplicationService, "product-1", new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .assertNext(value -> assertThat(value).isEqualTo(0))
                    .verifyComplete();
        }

        @Test
        @DisplayName("reserveFallback should return false")
        void reserveFallbackShouldReturnFalse() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("reserveFallback", String.class, String.class, int.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Boolean> result = (Mono<Boolean>) fallback.invoke(stockApplicationService, "order-1", "product-1", 5, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .assertNext(value -> assertThat(value).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("releaseFallback should return empty Mono")
        void releaseFallbackShouldReturnEmpty() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("releaseFallback", String.class, String.class, int.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallback.invoke(stockApplicationService, "order-1", "product-1", 5, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("confirmDispatchFallback should return empty Mono")
        void confirmDispatchFallbackShouldReturnEmpty() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("confirmDispatchFallback", String.class, String.class, int.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallback.invoke(stockApplicationService, "order-1", "product-1", 5, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("getProductFallback should return empty Mono")
        void getProductFallbackShouldReturnEmpty() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("getProductFallback", String.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Product> result = (Mono<Product>) fallback.invoke(stockApplicationService, "product-1", new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("getAllProductsFallback should return empty Flux")
        void getAllProductsFallbackShouldReturnEmpty() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("getAllProductsFallback", Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Flux<Product> result = (Flux<Product>) fallback.invoke(stockApplicationService, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("createProductFallback should return error")
        void createProductFallbackShouldReturnError() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("createProductFallback", Product.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Product> result = (Mono<Product>) fallback.invoke(stockApplicationService, testProduct, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().contains("Service temporarily unavailable"))
                    .verify();
        }

        @Test
        @DisplayName("updateStockFallback should return error")
        void updateStockFallbackShouldReturnError() throws Exception {
            Method fallback = StockApplicationService.class.getDeclaredMethod("updateStockFallback", String.class, int.class, Throwable.class);
            fallback.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Product> result = (Mono<Product>) fallback.invoke(stockApplicationService, "product-1", 100, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().contains("Service temporarily unavailable"))
                    .verify();
        }
    }
}
