package com.venta.application.saga;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.DespachoEventPublisher;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SagaReconcilerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StockEventPublisher stockEventPublisher;
    @Mock
    private DespachoEventPublisher despachoEventPublisher;

    private SagaReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new SagaReconciler(orderRepository, stockEventPublisher,
                despachoEventPublisher, Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("Should re-emit stock reserve for orders stuck in PENDING")
    void shouldRedrivePendingOrders() {
        Order stuck = Order.builder()
                .id("order-1").productId("product-1").quantity(3)
                .status(OrderStatus.PENDING)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(orderRepository.findByStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(Flux.just(stuck));

        StepVerifier.create(reconciler.reconcile()).verifyComplete();

        ArgumentCaptor<StockReserveEvent> captor = ArgumentCaptor.forClass(StockReserveEvent.class);
        verify(stockEventPublisher).reserveStock(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        verify(despachoEventPublisher, never()).requestDespacho(any());
    }

    @Test
    @DisplayName("Should re-emit despacho request for orders stuck in STOCK_RESERVED")
    void shouldRedriveStockReservedOrders() {
        Order stuck = Order.builder()
                .id("order-2").productId("product-2").quantity(1).customerId("cust-2")
                .status(OrderStatus.STOCK_RESERVED)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(orderRepository.findByStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(Flux.just(stuck));

        StepVerifier.create(reconciler.reconcile()).verifyComplete();

        ArgumentCaptor<DespachoRequestEvent> captor = ArgumentCaptor.forClass(DespachoRequestEvent.class);
        verify(despachoEventPublisher).requestDespacho(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-2");
        verify(stockEventPublisher, never()).reserveStock(any());
    }

    @Test
    @DisplayName("Should query only intermediate statuses")
    void shouldQueryIntermediateStatuses() {
        when(orderRepository.findByStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(reconciler.reconcile()).verifyComplete();

        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).findByStatusInAndUpdatedAtBefore(statuses.capture(), any());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.STOCK_RESERVED);
    }

    @Test
    @DisplayName("Should complete when no stuck orders")
    void shouldCompleteWhenNoStuckOrders() {
        when(orderRepository.findByStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(reconciler.reconcile()).verifyComplete();

        verify(stockEventPublisher, never()).reserveStock(any());
        verify(despachoEventPublisher, never()).requestDespacho(any());
    }
}
