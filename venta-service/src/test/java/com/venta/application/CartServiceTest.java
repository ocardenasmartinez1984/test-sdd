package com.venta.application;

import com.venta.domain.model.CartItem;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.CartRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private StockEventPublisher stockEventPublisher;

    @InjectMocks
    private CartService cartService;

    private CartItem testCartItem;

    @BeforeEach
    void setUp() {
        testCartItem = CartItem.builder()
                .id("cart-item-1")
                .sessionId("session-1")
                .productId("product-1")
                .quantity(2)
                .unitPrice(25.50)
                .status(CartItem.STATUS_RESERVED)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    @Nested
    @DisplayName("Add To Cart Tests")
    class AddToCartTests {

        @Test
        @DisplayName("Should add new item to cart when product not in cart")
        void shouldAddNewItemToCart() {
            when(cartRepository.findBySessionIdAndProductId("session-1", "product-1"))
                    .thenReturn(Mono.empty());
            when(cartRepository.save(any(CartItem.class))).thenReturn(Mono.just(testCartItem));
            doNothing().when(stockEventPublisher).reserveStock(any());

            StepVerifier.create(cartService.addToCart("session-1", "product-1", 2, 25.50))
                    .assertNext(item -> {
                        assertThat(item.getId()).isEqualTo("cart-item-1");
                        assertThat(item.getSessionId()).isEqualTo("session-1");
                        assertThat(item.getProductId()).isEqualTo("product-1");
                        assertThat(item.getQuantity()).isEqualTo(2);
                        assertThat(item.getStatus()).isEqualTo(CartItem.STATUS_RESERVED);
                    })
                    .verifyComplete();

            verify(cartRepository).save(any(CartItem.class));
            verify(stockEventPublisher).reserveStock(any());
        }

        @Test
        @DisplayName("Should increment quantity when product already in cart")
        void shouldIncrementQuantityWhenProductAlreadyInCart() {
            CartItem existingItem = CartItem.builder()
                    .id("cart-item-1")
                    .sessionId("session-1")
                    .productId("product-1")
                    .quantity(2)
                    .unitPrice(25.50)
                    .status(CartItem.STATUS_RESERVED)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();

            CartItem updatedItem = CartItem.builder()
                    .id("cart-item-1")
                    .sessionId("session-1")
                    .productId("product-1")
                    .quantity(5)  // 2 + 3
                    .unitPrice(25.50)
                    .status(CartItem.STATUS_RESERVED)
                    .createdAt(existingItem.getCreatedAt())
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();

            when(cartRepository.findBySessionIdAndProductId("session-1", "product-1"))
                    .thenReturn(Mono.just(existingItem));
            when(cartRepository.save(any(CartItem.class))).thenReturn(Mono.just(updatedItem));
            doNothing().when(stockEventPublisher).reserveStock(any());

            StepVerifier.create(cartService.addToCart("session-1", "product-1", 3, 25.50))
                    .assertNext(item -> {
                        assertThat(item.getQuantity()).isEqualTo(5);
                        assertThat(item.getProductId()).isEqualTo("product-1");
                    })
                    .verifyComplete();

            verify(cartRepository).save(any(CartItem.class));
            verify(stockEventPublisher).reserveStock(any());
        }
    }

    @Nested
    @DisplayName("Remove From Cart Tests")
    class RemoveFromCartTests {

        @Test
        @DisplayName("Should send compensate event and delete item from cart")
        void shouldSendCompensateAndDeleteItem() {
            when(cartRepository.findBySessionIdAndProductId("session-1", "product-1"))
                    .thenReturn(Mono.just(testCartItem));
            doNothing().when(stockEventPublisher).compensateStock(any());
            when(cartRepository.delete(testCartItem)).thenReturn(Mono.empty());

            StepVerifier.create(cartService.removeFromCart("session-1", "product-1"))
                    .verifyComplete();

            verify(stockEventPublisher).compensateStock(any());
            verify(cartRepository).delete(testCartItem);
        }

        @Test
        @DisplayName("Should complete without action when item not found")
        void shouldCompleteWithoutActionWhenItemNotFound() {
            when(cartRepository.findBySessionIdAndProductId("session-1", "product-1"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(cartService.removeFromCart("session-1", "product-1"))
                    .verifyComplete();

            verify(stockEventPublisher, never()).compensateStock(any());
            verify(cartRepository, never()).delete(any(CartItem.class));
        }
    }

    @Nested
    @DisplayName("Get Cart Tests")
    class GetCartTests {

        @Test
        @DisplayName("Should return only RESERVED items for session")
        void shouldReturnOnlyReservedItems() {
            CartItem reservedItem = CartItem.builder()
                    .id("cart-item-1")
                    .sessionId("session-1")
                    .productId("product-1")
                    .quantity(2)
                    .status(CartItem.STATUS_RESERVED)
                    .build();

            when(cartRepository.findBySessionIdAndStatus("session-1", CartItem.STATUS_RESERVED))
                    .thenReturn(Flux.just(reservedItem));

            StepVerifier.create(cartService.getCart("session-1"))
                    .assertNext(item -> {
                        assertThat(item.getStatus()).isEqualTo(CartItem.STATUS_RESERVED);
                        assertThat(item.getProductId()).isEqualTo("product-1");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when no RESERVED items")
        void shouldReturnEmptyWhenNoReservedItems() {
            when(cartRepository.findBySessionIdAndStatus("session-1", CartItem.STATUS_RESERVED))
                    .thenReturn(Flux.empty());

            StepVerifier.create(cartService.getCart("session-1"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Clear Cart Tests")
    class ClearCartTests {

        @Test
        @DisplayName("Should send compensate for each reserved item and delete all")
        void shouldSendCompensateForEachAndDeleteAll() {
            CartItem item1 = CartItem.builder()
                    .id("item-1")
                    .sessionId("session-1")
                    .productId("product-1")
                    .quantity(2)
                    .status(CartItem.STATUS_RESERVED)
                    .build();

            CartItem item2 = CartItem.builder()
                    .id("item-2")
                    .sessionId("session-1")
                    .productId("product-2")
                    .quantity(3)
                    .status(CartItem.STATUS_RESERVED)
                    .build();

            when(cartRepository.findBySessionId("session-1"))
                    .thenReturn(Flux.just(item1, item2));
            doNothing().when(stockEventPublisher).compensateStock(any());
            when(cartRepository.deleteAll(anyList())).thenReturn(Mono.empty());

            StepVerifier.create(cartService.clearCart("session-1"))
                    .verifyComplete();

            verify(stockEventPublisher, times(2)).compensateStock(any());
        }

        @Test
        @DisplayName("Should handle empty cart on clear")
        void shouldHandleEmptyCartOnClear() {
            when(cartRepository.findBySessionId("session-1"))
                    .thenReturn(Flux.empty());
            when(cartRepository.deleteAll(anyList())).thenReturn(Mono.empty());

            StepVerifier.create(cartService.clearCart("session-1"))
                    .verifyComplete();

            verify(stockEventPublisher, never()).compensateStock(any());
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class FallbackTests {

        @Test
        @DisplayName("addToCartFallback should return Mono.error with unavailable message")
        void addToCartFallbackShouldReturnError() throws Exception {
            Method fallbackMethod = CartService.class.getDeclaredMethod(
                    "addToCartFallback", String.class, String.class, int.class, double.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<CartItem> result = (Mono<CartItem>) fallbackMethod.invoke(
                    cartService, "session-1", "product-1", 2, 25.50,
                    new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cart service temporarily unavailable"))
                    .verify();
        }

        @Test
        @DisplayName("removeFromCartFallback should return Mono.error with unavailable message")
        void removeFromCartFallbackShouldReturnError() throws Exception {
            Method fallbackMethod = CartService.class.getDeclaredMethod(
                    "removeFromCartFallback", String.class, String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallbackMethod.invoke(
                    cartService, "session-1", "product-1",
                    new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cart service temporarily unavailable"))
                    .verify();
        }

        @Test
        @DisplayName("getCartFallback should return empty Flux")
        void getCartFallbackShouldReturnEmpty() throws Exception {
            Method fallbackMethod = CartService.class.getDeclaredMethod(
                    "getCartFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Flux<CartItem> result = (Flux<CartItem>) fallbackMethod.invoke(
                    cartService, "session-1", new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("clearCartFallback should return Mono.error with unavailable message")
        void clearCartFallbackShouldReturnError() throws Exception {
            Method fallbackMethod = CartService.class.getDeclaredMethod(
                    "clearCartFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallbackMethod.invoke(
                    cartService, "session-1", new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cart service temporarily unavailable"))
                    .verify();
        }
    }
}
