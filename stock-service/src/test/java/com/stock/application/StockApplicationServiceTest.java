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
                .reservedByOrder(new java.util.HashMap<>(java.util.Map.of("existing-order", 10)))
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

        // Simula que el repositorio confirma la existencia del producto "product-1"
        // e invoca exists(); verifica que el Mono emite true.
        @Test
        @DisplayName("Should return true when product exists")
        void shouldReturnTrueWhenProductExists() {
            when(productRepository.existsById("product-1")).thenReturn(Mono.just(true));

            StepVerifier.create(stockApplicationService.exists("product-1"))
                    .assertNext(exists -> assertThat(exists).isTrue())
                    .verifyComplete();
        }

        // Simula que el repositorio indica que "nonexistent" no existe e invoca
        // exists(); verifica que el Mono emite false.
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

        // Con stock disponible de 90 (100 - 10 reservados), solicita 50 unidades
        // mediante isAvailable(); verifica que devuelve true por haber stock suficiente.
        @Test
        @DisplayName("Should return true when sufficient stock available")
        void shouldReturnTrueWhenSufficientStock() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            // available = 100 - 10 = 90, requesting 50
            StepVerifier.create(stockApplicationService.isAvailable("product-1", 50))
                    .assertNext(available -> assertThat(available).isTrue())
                    .verifyComplete();
        }

        // Solicita exactamente la cantidad disponible (90) mediante isAvailable();
        // verifica que devuelve true porque el límite exacto se considera disponible.
        @Test
        @DisplayName("Should return true when requesting exact available quantity")
        void shouldReturnTrueWhenRequestingExactAvailable() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            // available = 100 - 10 = 90, requesting 90
            StepVerifier.create(stockApplicationService.isAvailable("product-1", 90))
                    .assertNext(available -> assertThat(available).isTrue())
                    .verifyComplete();
        }

        // Solicita 100 unidades cuando solo hay 90 disponibles mediante isAvailable();
        // verifica que devuelve false por stock insuficiente.
        @Test
        @DisplayName("Should return false when insufficient stock")
        void shouldReturnFalseWhenInsufficientStock() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            // available = 100 - 10 = 90, requesting 100
            StepVerifier.create(stockApplicationService.isAvailable("product-1", 100))
                    .assertNext(available -> assertThat(available).isFalse())
                    .verifyComplete();
        }

        // Simula que el producto no existe en el repositorio e invoca isAvailable();
        // verifica que devuelve false cuando no se encuentra el producto.
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

        // Invoca getAvailableQuantity() sobre un producto con 100 unidades y 10 reservadas;
        // verifica que la cantidad disponible calculada es 90 (100 - 10).
        @Test
        @DisplayName("Should return correct available quantity")
        void shouldReturnCorrectAvailableQuantity() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            StepVerifier.create(stockApplicationService.getAvailableQuantity("product-1"))
                    .assertNext(quantity -> assertThat(quantity).isEqualTo(90)) // 100 - 10
                    .verifyComplete();
        }

        // Simula que el producto no existe e invoca getAvailableQuantity();
        // verifica que devuelve 0 cuando el producto no se encuentra.
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

        // Con stock disponible, invoca reserve() para 50 unidades; verifica que devuelve
        // true, que persiste el producto (save) y que invalida la caché (evictProduct).
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

        // Invoca reserve() para 100 unidades cuando solo hay 90 disponibles; verifica que
        // devuelve false y que no se persiste ni se invalida la caché.
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

        // Simula que el producto no existe e invoca reserve(); verifica que devuelve
        // false y que nunca se invalida la caché.
        @Test
        @DisplayName("Should fail reserve when product not found")
        void shouldFailReserveWhenProductNotFound() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.reserve("order-1", "nonexistent", 5))
                    .assertNext(success -> assertThat(success).isFalse())
                    .verifyComplete();

            verify(productCacheService, never()).evictProduct(anyString());
        }

        // Realiza una reserva exitosa de 5 unidades e invoca reserve(); verifica que
        // tras la reserva se invalida la caché del producto (evictProduct).
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

        // Reserva de nuevo la misma cantidad (10) para un pedido que ya tenía 10 reservadas;
        // verifica idempotencia: la cantidad reservada no cambia y no se persiste ni se
        // invalida la caché al no haber delta.
        @Test
        @DisplayName("Should be idempotent: re-reserving same quantity for same order does not double-count")
        void shouldBeIdempotentForSameOrder() {
            // testProduct already reserves 10 for "existing-order" (reservedQuantity=10)
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            StepVerifier.create(stockApplicationService.reserve("existing-order", "product-1", 10))
                    .assertNext(success -> assertThat(success).isTrue())
                    .verifyComplete();

            // Zero delta: nothing persisted, reserved quantity unchanged
            assertThat(testProduct.getReservedQuantity()).isEqualTo(10);
            verify(productRepository, never()).save(any(Product.class));
            verify(productCacheService, never()).evictProduct(anyString());
        }

        // Un pedido que ya reservaba 10 solicita ahora 15 en total; verifica que solo se
        // aplica el delta (+5), que la cantidad reservada pasa a 15 y el mapa por pedido lo
        // refleja, persistiendo e invalidando la caché.
        @Test
        @DisplayName("Should apply only the delta when an order updates its reserved quantity (cart add-more)")
        void shouldApplyDeltaWhenQuantityIncreases() {
            // "existing-order" already reserves 10; now it wants 15 total (e.g. added 5 more to cart)
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.reserve("existing-order", "product-1", 15))
                    .assertNext(success -> assertThat(success).isTrue())
                    .verifyComplete();

            // reservedQuantity goes 10 -> 15 (delta +5), and the per-order map reflects 15
            assertThat(testProduct.getReservedQuantity()).isEqualTo(15);
            assertThat(testProduct.getReservedByOrder().get("existing-order")).isEqualTo(15);
            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }
    }

    @Nested
    @DisplayName("Release Tests")
    class ReleaseTests {

        // Invoca release() para liberar 5 unidades reservadas de un pedido existente;
        // verifica que se persiste el producto y se invalida su caché.
        @Test
        @DisplayName("Should release reserved stock and evict cache")
        void shouldReleaseReservedStockAndEvictCache() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(testProduct));
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.release("existing-order", "product-1", 5))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }

        // Con solo 3 unidades reservadas, invoca release() liberando 10; verifica que la
        // cantidad reservada no baja de 0 (el save comprueba que sea >= 0).
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

            StepVerifier.create(stockApplicationService.release("existing-order", "product-1", 10))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }

        // Simula que el producto no existe e invoca release(); verifica que el flujo
        // completa sin error y que no se persiste ni se invalida la caché.
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

        // Invoca confirmDispatch() para un pedido existente; verifica que reduce la
        // cantidad total (a 90) y la reservada (a 0), persiste el producto e invalida la caché.
        @Test
        @DisplayName("Should confirm dispatch by reducing quantity and reserved, then evict cache")
        void shouldConfirmDispatchAndEvictCache() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                assertThat(saved.getQuantity()).isEqualTo(90); // 100 - 10 (reserved by existing-order)
                assertThat(saved.getReservedQuantity()).isEqualTo(0); // 10 - 10
                return Mono.just(saved);
            });
            when(productCacheService.evictProduct("product-1")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.confirmDispatch("existing-order", "product-1", 5))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
            verify(productCacheService).evictProduct("product-1");
        }

        // Simula que el producto no existe e invoca confirmDispatch(); verifica que el
        // flujo completa sin error y que no se persiste ni se invalida la caché.
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

        // Simula un acierto de caché (cache hit) e invoca getProduct(); verifica que el
        // producto se devuelve desde la caché sin consultar el repositorio.
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

        // Simula un fallo de caché (cache miss) e invoca getProduct(); verifica que se
        // consulta el repositorio, se devuelve el producto y se guarda en caché (cacheProduct).
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

        // Simula fallo de caché y producto ausente en la BD e invoca getProduct(); verifica
        // que devuelve un Mono vacío y que nunca se intenta cachear.
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

        // Simula que el repositorio devuelve un producto e invoca getAllProducts();
        // verifica que el Flux emite dicho producto.
        @Test
        @DisplayName("Should get all products from repository")
        void shouldGetAllProducts() {
            when(productRepository.findAll()).thenReturn(Flux.just(testProduct));

            StepVerifier.create(stockApplicationService.getAllProducts())
                    .assertNext(product -> assertThat(product.getId()).isEqualTo("product-1"))
                    .verifyComplete();
        }

        // Simula que el repositorio no tiene productos e invoca getAllProducts();
        // verifica que el Flux completa vacío.
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

        // Crea un producto nuevo sin cantidad reservada e invoca createProduct(); verifica
        // que se inicializa la reservada en 0, se persiste y se guarda en caché.
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

        // Crea un producto que ya trae cantidad reservada (5) e invoca createProduct();
        // verifica que se preserva ese valor en lugar de reiniciarlo a 0.
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

        // Invoca updateStock() para fijar la cantidad en 200; verifica que se actualiza la
        // cantidad del producto y que se invalida su caché (evictProduct).
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

        // Simula que el producto no existe e invoca updateStock(); verifica que devuelve
        // vacío y que no se persiste ni se invalida la caché.
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

        // Invoca por reflexión el fallback existsFallback ante un fallo simulado;
        // verifica que devuelve un Mono con false.
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

        // Invoca por reflexión el fallback isAvailableFallback ante un fallo simulado;
        // verifica que devuelve un Mono con false.
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

        // Invoca por reflexión el fallback getAvailableQuantityFallback ante un fallo;
        // verifica que devuelve un Mono con 0.
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

        // Invoca por reflexión el fallback reserveFallback ante un fallo simulado;
        // verifica que devuelve un Mono con false.
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

        // Invoca por reflexión el fallback releaseFallback ante un fallo simulado;
        // verifica que devuelve un Mono vacío que completa sin error.
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

        // Invoca por reflexión el fallback confirmDispatchFallback ante un fallo simulado;
        // verifica que devuelve un Mono vacío que completa sin error.
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

        // Invoca por reflexión el fallback getProductFallback ante un fallo simulado;
        // verifica que devuelve un Mono vacío que completa sin error.
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

        // Invoca por reflexión el fallback getAllProductsFallback ante un fallo simulado;
        // verifica que devuelve un Flux vacío que completa sin error.
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

        // Invoca por reflexión el fallback createProductFallback ante un fallo simulado;
        // verifica que emite un error con el mensaje "Service temporarily unavailable".
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

        // Invoca por reflexión el fallback updateStockFallback ante un fallo simulado;
        // verifica que emite un error con el mensaje "Service temporarily unavailable".
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
