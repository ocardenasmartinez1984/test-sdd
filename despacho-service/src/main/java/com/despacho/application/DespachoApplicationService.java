package com.despacho.application;

import com.despacho.domain.event.DespachoRequestEvent;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import com.despacho.domain.repository.DispatchRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Servicio de aplicación que orquesta los casos de uso del despacho.
 *
 * <p>Es la pieza central de la capa de aplicación (DDD): coordina la creación y
 * actualización de despachos delegando la persistencia en
 * {@link DispatchRepository} (MongoDB reactivo) y publicando eventos en Kafka
 * mediante {@link KafkaTemplate}. Todas las operaciones que tocan MongoDB o
 * Kafka están protegidas con Resilience4j {@code @CircuitBreaker} y cuentan con
 * un método de fallback que degrada la respuesta de forma controlada cuando la
 * dependencia falla.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DespachoApplicationService {

    private final DispatchRepository dispatchRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Crea un nuevo despacho a partir de una solicitud recibida del flujo SAGA.
     *
     * <p>Genera un número de seguimiento único con prefijo {@code TRK-},
     * construye la entidad {@link Dispatch} en estado inicial
     * {@link DispatchStatus#PREPARANDO} con las marcas de tiempo actuales y la
     * persiste de forma reactiva en MongoDB. Registra en el log el tracking y la
     * orden cuando el guardado tiene éxito.</p>
     *
     * <p>Protegido por el circuit breaker {@code mongoDB}; ante fallo se delega
     * en {@code crearDespachoFallback}.</p>
     *
     * @param request evento con los datos de la orden, producto, cantidad y cliente
     * @return {@link Mono} con el despacho persistido
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "crearDespachoFallback")
    public Mono<Dispatch> crearDespacho(DespachoRequestEvent request) {
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Dispatch dispatch = Dispatch.builder()
                .orderId(request.getOrderId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .customerId(request.getCustomerId())
                .trackingNumber(trackingNumber)
                .status(DispatchStatus.PREPARANDO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return dispatchRepository.save(dispatch)
                .doOnSuccess(saved -> log.info("Despacho creado con tracking: {} para orden: {}", trackingNumber, request.getOrderId()));
    }

    /**
     * Actualiza el estado de un despacho existente.
     *
     * <p>Busca el despacho por su identificador, le asigna el nuevo estado y la
     * marca de tiempo de actualización, y lo vuelve a persistir. Si el estado
     * resultante es {@link DispatchStatus#ENTREGADO}, dispara la notificación de
     * entrega vía Kafka mediante {@link #notifyDelivered(String)}.</p>
     *
     * <p>Protegido por el circuit breaker {@code mongoDB}; ante fallo se delega
     * en {@code actualizarEstadoFallback}.</p>
     *
     * @param id          identificador del despacho a actualizar
     * @param nuevoEstado estado al que se debe transicionar el despacho
     * @return {@link Mono} con el despacho actualizado, o vacío si no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "actualizarEstadoFallback")
    public Mono<Dispatch> actualizarEstado(String id, DispatchStatus nuevoEstado) {
        return dispatchRepository.findById(id)
                .flatMap(dispatch -> {
                    dispatch.setStatus(nuevoEstado);
                    dispatch.setUpdatedAt(LocalDateTime.now());
                    return dispatchRepository.save(dispatch);
                })
                .doOnSuccess(updated -> {
                    if (updated != null) {
                        log.info("Despacho {} actualizado a estado: {}", id, nuevoEstado);
                        if (nuevoEstado == DispatchStatus.ENTREGADO) {
                            notifyDelivered(updated.getOrderId());
                        }
                    }
                });
    }

    /**
     * Publica en Kafka la notificación de que una orden ha sido entregada.
     *
     * <p>Construye un mensaje con el {@code orderId} y lo envía al tópico
     * {@code saga.despacho.delivered}. Captura y registra cualquier excepción
     * para no interrumpir el flujo de actualización de estado.</p>
     *
     * <p>Protegido por el circuit breaker {@code kafkaProducer}; ante fallo se
     * delega en {@code notifyDeliveredFallback}.</p>
     *
     * @param orderId identificador de la orden entregada
     */
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "notifyDeliveredFallback")
    private void notifyDelivered(String orderId) {
        try {
            java.util.Map<String, String> event = java.util.Map.of("orderId", orderId);
            kafkaTemplate.send("saga.despacho.delivered", orderId, event);
            log.info("Notificación de entrega enviada para orden: {}", orderId);
        } catch (Exception e) {
            log.error("Error enviando notificación de entrega: {}", e.getMessage(), e);
        }
    }

    private void notifyDeliveredFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN [kafkaProducer] - Failed to notify delivery for order: {}. Error: {}", orderId, t.getMessage());
    }

    /**
     * Recupera el despacho asociado a un número de seguimiento.
     *
     * <p>Protegido por el circuit breaker {@code mongoDB}; ante fallo se delega
     * en {@code buscarPorTrackingFallback}, que devuelve un {@link Mono} vacío.</p>
     *
     * @param trackingNumber número de tracking del envío
     * @return {@link Mono} con el despacho encontrado, o vacío si no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "buscarPorTrackingFallback")
    public Mono<Dispatch> buscarPorTracking(String trackingNumber) {
        return dispatchRepository.findByTrackingNumber(trackingNumber);
    }

    /**
     * Recupera el despacho asociado a una orden de venta.
     *
     * <p>Protegido por el circuit breaker {@code mongoDB}; ante fallo se delega
     * en {@code buscarPorOrdenFallback}, que devuelve un {@link Mono} vacío.</p>
     *
     * @param orderId identificador de la orden
     * @return {@link Mono} con el despacho encontrado, o vacío si no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "buscarPorOrdenFallback")
    public Mono<Dispatch> buscarPorOrden(String orderId) {
        return dispatchRepository.findByOrderId(orderId);
    }

    /**
     * Lista los despachos que se encuentran en un estado determinado.
     *
     * <p>Protegido por el circuit breaker {@code mongoDB}; ante fallo se delega
     * en {@code listarPorEstadoFallback}, que devuelve un {@link Flux} vacío.</p>
     *
     * @param status estado del ciclo de vida por el que filtrar
     * @return {@link Flux} con los despachos en el estado indicado
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "listarPorEstadoFallback")
    public Flux<Dispatch> listarPorEstado(DispatchStatus status) {
        return dispatchRepository.findByStatus(status);
    }

    /**
     * Lista todos los despachos registrados.
     *
     * <p>Protegido por el circuit breaker {@code mongoDB}; ante fallo se delega
     * en {@code listarTodosFallback}, que devuelve un {@link Flux} vacío.</p>
     *
     * @return {@link Flux} con todos los despachos existentes
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "listarTodosFallback")
    public Flux<Dispatch> listarTodos() {
        return dispatchRepository.findAll();
    }

    // Fallback methods
    private Mono<Dispatch> crearDespachoFallback(DespachoRequestEvent request, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - crearDespacho failed for order: {}. Error: {}", request.getOrderId(), t.getMessage());
        return Mono.error(new RuntimeException("Dispatch service temporarily unavailable"));
    }

    private Mono<Dispatch> actualizarEstadoFallback(String id, DispatchStatus nuevoEstado, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - actualizarEstado failed for dispatch: {}. Error: {}", id, t.getMessage());
        return Mono.error(new RuntimeException("Dispatch service temporarily unavailable"));
    }

    private Mono<Dispatch> buscarPorTrackingFallback(String trackingNumber, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - buscarPorTracking failed. Error: {}", t.getMessage());
        return Mono.empty();
    }

    private Mono<Dispatch> buscarPorOrdenFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - buscarPorOrden failed. Error: {}", t.getMessage());
        return Mono.empty();
    }

    private Flux<Dispatch> listarPorEstadoFallback(DispatchStatus status, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - listarPorEstado failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Flux<Dispatch> listarTodosFallback(Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - listarTodos failed. Error: {}", t.getMessage());
        return Flux.empty();
    }
}
