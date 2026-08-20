package com.venta.interfaces.rest;

import com.venta.application.command.OrderCommandService;
import com.venta.application.query.OrderQueryService;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    @PostMapping
    public Mono<ResponseEntity<Order>> crearVenta(@RequestBody Order order) {
        return orderCommandService.crearVenta(order)
                .map(createdOrder -> ResponseEntity.status(HttpStatus.CREATED).body(createdOrder));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Order>> getVenta(@PathVariable String id) {
        return orderQueryService.getVenta(id)
                .map(ResponseEntity::ok);
    }

    @GetMapping
    public Flux<Order> listarVentas() {
        return orderQueryService.listarVentas();
    }

    @GetMapping("/customer/{customerId}")
    public Flux<Order> ventasPorCliente(@PathVariable String customerId) {
        return orderQueryService.ventasPorCliente(customerId);
    }

    @GetMapping("/status/{status}")
    public Flux<Order> ventasPorEstado(@PathVariable OrderStatus status) {
        return orderQueryService.ventasPorEstado(status);
    }

    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<Order>> cancelarVenta(@PathVariable String id) {
        return orderCommandService.cancelarVenta(id)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Order>> actualizarEstado(@PathVariable String id,
                                                        @RequestParam OrderStatus status) {
        return orderCommandService.actualizarEstado(id, status)
                .map(ResponseEntity::ok);
    }
}
