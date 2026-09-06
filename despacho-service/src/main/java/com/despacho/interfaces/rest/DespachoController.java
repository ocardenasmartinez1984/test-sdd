package com.despacho.interfaces.rest;

import com.despacho.application.DespachoApplicationService;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST reactivo que expone la API de despachos.
 *
 * <p>Es el adaptador de entrada HTTP (capa de interfaces) bajo la ruta base
 * {@code /api/v1/despachos}. Delega toda la lógica en
 * {@link DespachoApplicationService} y traduce los resultados reactivos a
 * respuestas HTTP, devolviendo {@code 404 Not Found} cuando no hay resultado.</p>
 */
@RestController
@RequestMapping("/api/v1/despachos")
@RequiredArgsConstructor
public class DespachoController {

    private final DespachoApplicationService despachoApplicationService;

    /**
     * Lista todos los despachos registrados.
     *
     * @return {@link Flux} con todos los despachos
     */
    @GetMapping
    public Flux<Dispatch> listarTodos() {
        return despachoApplicationService.listarTodos();
    }

    /**
     * Busca un despacho por su número de seguimiento.
     *
     * @param trackingNumber número de tracking del envío
     * @return {@code 200 OK} con el despacho, o {@code 404 Not Found} si no existe
     */
    @GetMapping("/tracking/{trackingNumber}")
    public Mono<ResponseEntity<Dispatch>> buscarPorTracking(@PathVariable String trackingNumber) {
        return despachoApplicationService.buscarPorTracking(trackingNumber)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Busca el despacho asociado a una orden.
     *
     * @param orderId identificador de la orden
     * @return {@code 200 OK} con el despacho, o {@code 404 Not Found} si no existe
     */
    @GetMapping("/order/{orderId}")
    public Mono<ResponseEntity<Dispatch>> buscarPorOrden(@PathVariable String orderId) {
        return despachoApplicationService.buscarPorOrden(orderId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Lista los despachos que se encuentran en un estado determinado.
     *
     * @param status estado del ciclo de vida por el que filtrar
     * @return {@link Flux} con los despachos en el estado indicado
     */
    @GetMapping("/status/{status}")
    public Flux<Dispatch> listarPorEstado(@PathVariable DispatchStatus status) {
        return despachoApplicationService.listarPorEstado(status);
    }

    /**
     * Actualiza el estado de un despacho.
     *
     * @param id     identificador del despacho a actualizar
     * @param status nuevo estado a asignar (parámetro de consulta)
     * @return {@code 200 OK} con el despacho actualizado, o {@code 404 Not Found}
     *         si no existe
     */
    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Dispatch>> actualizarEstado(@PathVariable String id,
                                                           @RequestParam DispatchStatus status) {
        return despachoApplicationService.actualizarEstado(id, status)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
