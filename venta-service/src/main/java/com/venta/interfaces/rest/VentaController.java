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

/**
 * Controlador REST reactivo que expone la API de órdenes de venta bajo
 * {@code /api/v1/ventas}.
 *
 * <p>Es el adaptador de entrada de la capa de interfaces: delega las escrituras
 * en {@link OrderCommandService} y las lecturas en {@link OrderQueryService},
 * traduciendo los resultados a respuestas HTTP.
 */
@RestController
@RequestMapping("/api/v1/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    /**
     * Crea una nueva orden e inicia la SAGA de venta.
     *
     * @param order datos de la orden a crear
     * @return la orden creada con estado HTTP 201
     */
    @PostMapping
    public Mono<ResponseEntity<Order>> crearVenta(@RequestBody Order order) {
        return orderCommandService.crearVenta(order)
                .map(createdOrder -> ResponseEntity.status(HttpStatus.CREATED).body(createdOrder));
    }

    /**
     * Obtiene una orden por su identificador.
     *
     * @param id identificador de la orden
     * @return la orden encontrada con estado HTTP 200
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Order>> getVenta(@PathVariable String id) {
        return orderQueryService.getVenta(id)
                .map(ResponseEntity::ok);
    }

    /**
     * Lista todas las órdenes.
     *
     * @return flujo con todas las órdenes
     */
    @GetMapping
    public Flux<Order> listarVentas() {
        return orderQueryService.listarVentas();
    }

    /**
     * Lista las órdenes de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo de órdenes del cliente
     */
    @GetMapping("/customer/{customerId}")
    public Flux<Order> ventasPorCliente(@PathVariable String customerId) {
        return orderQueryService.ventasPorCliente(customerId);
    }

    /**
     * Lista las órdenes que están en un estado dado.
     *
     * @param status estado por el que filtrar
     * @return flujo de órdenes en ese estado
     */
    @GetMapping("/status/{status}")
    public Flux<Order> ventasPorEstado(@PathVariable OrderStatus status) {
        return orderQueryService.ventasPorEstado(status);
    }

    /**
     * Cancela una orden y, si procede, compensa su stock reservado.
     *
     * @param id identificador de la orden a cancelar
     * @return la orden cancelada con estado HTTP 200
     */
    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<Order>> cancelarVenta(@PathVariable String id) {
        return orderCommandService.cancelarVenta(id)
                .map(ResponseEntity::ok);
    }

    /**
     * Actualiza el estado de una orden de forma directa.
     *
     * @param id identificador de la orden
     * @param status nuevo estado a asignar
     * @return la orden actualizada con estado HTTP 200
     */
    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Order>> actualizarEstado(@PathVariable String id,
                                                        @RequestParam OrderStatus status) {
        return orderCommandService.actualizarEstado(id, status)
                .map(ResponseEntity::ok);
    }
}
