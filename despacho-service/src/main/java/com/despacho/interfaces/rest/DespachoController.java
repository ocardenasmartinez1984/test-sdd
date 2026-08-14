package com.despacho.interfaces.rest;

import com.despacho.application.DespachoApplicationService;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/despachos")
@RequiredArgsConstructor
public class DespachoController {

    private final DespachoApplicationService despachoApplicationService;

    @GetMapping
    public Flux<Dispatch> listarTodos() {
        return despachoApplicationService.listarTodos();
    }

    @GetMapping("/tracking/{trackingNumber}")
    public Mono<ResponseEntity<Dispatch>> buscarPorTracking(@PathVariable String trackingNumber) {
        return despachoApplicationService.buscarPorTracking(trackingNumber)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderId}")
    public Mono<ResponseEntity<Dispatch>> buscarPorOrden(@PathVariable String orderId) {
        return despachoApplicationService.buscarPorOrden(orderId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public Flux<Dispatch> listarPorEstado(@PathVariable DispatchStatus status) {
        return despachoApplicationService.listarPorEstado(status);
    }

    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Dispatch>> actualizarEstado(@PathVariable String id,
                                                           @RequestParam DispatchStatus status) {
        return despachoApplicationService.actualizarEstado(id, status)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
