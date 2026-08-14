package com.stock.application;

import com.stock.domain.model.Product;
import com.stock.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

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

            StepVerifier.create(stockApplicationService.reserve("order-1", "product-1", 50))
                    .assertNext(success -> assertThat(success).isTrue())
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
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
        }

        @Test
        @DisplayName("Should fail reserve when product not found")
        void shouldFailReserveWhenProductNotFound() {
            when(productRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(stockApplicationService.reserve("order-1", "nonexistent", 5))
                    .assertNext(success -> assertThat(success).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Release Tests")
    class ReleaseTests {

        @Test
        @DisplayName("Should release reserved stock")
        void shouldReleaseReservedStock() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(testProduct));

            StepVerifier.create(stockApplicationService.release("order-1", "product-1", 5))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
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

            StepVerifier.create(stockApplicationService.release("order-1", "product-1", 10))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("ConfirmDispatch Tests")
    class ConfirmDispatchTests {

        @Test
        @DisplayName("Should confirm dispatch by reducing quantity and reserved")
        void shouldConfirmDispatch() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                assertThat(saved.getQuantity()).isEqualTo(95); // 100 - 5
                assertThat(saved.getReservedQuantity()).isEqualTo(5); // 10 - 5
                return Mono.just(saved);
            });

            StepVerifier.create(stockApplicationService.confirmDispatch("order-1", "product-1", 5))
                    .verifyComplete();

            verify(productRepository).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("Product CRUD Tests")
    class ProductCrudTests {

        @Test
        @DisplayName("Should get product by id")
        void shouldGetProductById() {
            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));

            StepVerifier.create(stockApplicationService.getProduct("product-1"))
                    .assertNext(product -> {
                        assertThat(product.getId()).isEqualTo("product-1");
                        assertThat(product.getName()).isEqualTo("Test Product");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should get all products")
        void shouldGetAllProducts() {
            when(productRepository.findAll()).thenReturn(Flux.just(testProduct));

            StepVerifier.create(stockApplicationService.getAllProducts())
                    .assertNext(product -> assertThat(product.getId()).isEqualTo("product-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should create product with default reserved quantity")
        void shouldCreateProductWithDefaultReservedQuantity() {
            Product newProduct = Product.builder()
                    .sku("SKU-002")
                    .name("New Product")
                    .quantity(50)
                    .price(19.99)
                    .build();

            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                saved.setId("product-2");
                return Mono.just(saved);
            });

            StepVerifier.create(stockApplicationService.createProduct(newProduct))
                    .assertNext(product -> {
                        assertThat(product.getReservedQuantity()).isEqualTo(0);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should update stock quantity")
        void shouldUpdateStockQuantity() {
            Product updatedProduct = Product.builder()
                    .id("product-1")
                    .quantity(200)
                    .build();

            when(productRepository.findById("product-1")).thenReturn(Mono.just(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(Mono.just(updatedProduct));

            StepVerifier.create(stockApplicationService.updateStock("product-1", 200))
                    .assertNext(product -> assertThat(product.getQuantity()).isEqualTo(200))
                    .verifyComplete();
        }
    }
}
