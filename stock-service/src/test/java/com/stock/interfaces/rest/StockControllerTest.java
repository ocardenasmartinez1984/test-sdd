package com.stock.interfaces.rest;

import com.stock.application.StockApplicationService;
import com.stock.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("StockController Unit Tests")
class StockControllerTest {

    @Mock
    private StockApplicationService stockApplicationService;

    @InjectMocks
    private StockController stockController;

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

    // Simula que el servicio devuelve un producto e invoca getAllProducts() del controlador;
    // verifica que el Flux emite el producto esperado con su id y nombre.
    @Test
    @DisplayName("Should get all products")
    void shouldGetAllProducts() {
        when(stockApplicationService.getAllProducts()).thenReturn(Flux.just(testProduct));

        StepVerifier.create(stockController.getAllProducts())
                .assertNext(product -> {
                    assertThat(product.getId()).isEqualTo("product-1");
                    assertThat(product.getName()).isEqualTo("Test Product");
                })
                .verifyComplete();
    }

    // Simula que el servicio encuentra el producto e invoca getProduct(); verifica que la
    // respuesta es HTTP 200 con el cuerpo del producto solicitado.
    @Test
    @DisplayName("Should get product by id")
    void shouldGetProductById() {
        when(stockApplicationService.getProduct("product-1")).thenReturn(Mono.just(testProduct));

        StepVerifier.create(stockController.getProduct("product-1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getId()).isEqualTo("product-1");
                })
                .verifyComplete();
    }

    // Simula que el servicio no encuentra el producto e invoca getProduct(); verifica que
    // la respuesta es HTTP 404 NOT_FOUND.
    @Test
    @DisplayName("Should return 404 when product not found")
    void shouldReturn404WhenProductNotFound() {
        when(stockApplicationService.getProduct("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(stockController.getProduct("nonexistent"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .verifyComplete();
    }

    // Simula que el producto existe y tiene 90 disponibles e invoca getAvailableQuantity();
    // verifica que la respuesta es HTTP 200 con la entrada availableQuantity = 90.
    @Test
    @DisplayName("Should get available quantity")
    void shouldGetAvailableQuantity() {
        when(stockApplicationService.exists("product-1")).thenReturn(Mono.just(true));
        when(stockApplicationService.getAvailableQuantity("product-1")).thenReturn(Mono.just(90));

        StepVerifier.create(stockController.getAvailableQuantity("product-1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).containsEntry("availableQuantity", 90);
                })
                .verifyComplete();
    }

    // Simula que el producto no existe e invoca getAvailableQuantity(); verifica que la
    // respuesta es HTTP 404 NOT_FOUND.
    @Test
    @DisplayName("Should return 404 for available quantity of non-existent product")
    void shouldReturn404ForAvailableQuantityOfNonExistentProduct() {
        when(stockApplicationService.exists("nonexistent")).thenReturn(Mono.just(false));

        StepVerifier.create(stockController.getAvailableQuantity("nonexistent"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .verifyComplete();
    }

    // Simula que el producto existe e invoca exists() del controlador; verifica que la
    // respuesta es HTTP 200 con la entrada exists = true.
    @Test
    @DisplayName("Should check if product exists")
    void shouldCheckIfProductExists() {
        when(stockApplicationService.exists("product-1")).thenReturn(Mono.just(true));

        StepVerifier.create(stockController.exists("product-1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).containsEntry("exists", true);
                })
                .verifyComplete();
    }

    // Simula que el servicio crea el producto e invoca createProduct(); verifica que la
    // respuesta es HTTP 200 con el cuerpo del producto creado.
    @Test
    @DisplayName("Should create product")
    void shouldCreateProduct() {
        when(stockApplicationService.createProduct(any(Product.class))).thenReturn(Mono.just(testProduct));

        StepVerifier.create(stockController.createProduct(testProduct))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getName()).isEqualTo("Test Product");
                })
                .verifyComplete();
    }

    // Invoca updateStock() con un mapa {quantity:200}; verifica que la respuesta es HTTP 200
    // y que el producto devuelto refleja la nueva cantidad 200.
    @Test
    @DisplayName("Should update stock quantity")
    void shouldUpdateStockQuantity() {
        Product updatedProduct = Product.builder().id("product-1").quantity(200).build();
        when(stockApplicationService.updateStock("product-1", 200)).thenReturn(Mono.just(updatedProduct));

        StepVerifier.create(stockController.updateStock("product-1", Map.of("quantity", 200)))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getQuantity()).isEqualTo(200);
                })
                .verifyComplete();
    }

    // Invoca updateStock() con un mapa vacío (sin quantity); verifica que la respuesta es
    // HTTP 400 BAD_REQUEST por falta del campo requerido.
    @Test
    @DisplayName("Should return bad request when quantity is missing")
    void shouldReturnBadRequestWhenQuantityMissing() {
        StepVerifier.create(stockController.updateStock("product-1", Map.of()))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .verifyComplete();
    }

    // Simula que el servicio no encuentra el producto al actualizar e invoca updateStock();
    // verifica que la respuesta es HTTP 404 NOT_FOUND.
    @Test
    @DisplayName("Should return 404 when updating non-existent product")
    void shouldReturn404WhenUpdatingNonExistentProduct() {
        when(stockApplicationService.updateStock("nonexistent", 100)).thenReturn(Mono.empty());

        StepVerifier.create(stockController.updateStock("nonexistent", Map.of("quantity", 100)))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .verifyComplete();
    }
}
