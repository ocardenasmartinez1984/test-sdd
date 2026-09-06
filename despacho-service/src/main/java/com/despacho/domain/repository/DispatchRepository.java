package com.despacho.domain.repository;

import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de acceso a datos para la entidad {@link Dispatch}.
 *
 * <p>Pertenece a la capa de dominio y abstrae la persistencia de despachos sobre
 * MongoDB mediante Spring Data Reactive. Además de las operaciones CRUD reactivas
 * heredadas de {@link ReactiveMongoRepository}, define consultas derivadas para
 * localizar despachos por orden, por número de seguimiento y por estado.</p>
 */
@Repository
public interface DispatchRepository extends ReactiveMongoRepository<Dispatch, String> {

    /**
     * Busca el despacho asociado a una orden de venta.
     *
     * @param orderId identificador de la orden
     * @return {@link Mono} con el despacho encontrado, o vacío si no existe
     */
    Mono<Dispatch> findByOrderId(String orderId);

    /**
     * Busca el despacho identificado por su número de seguimiento.
     *
     * @param trackingNumber número de tracking generado para el envío
     * @return {@link Mono} con el despacho encontrado, o vacío si no existe
     */
    Mono<Dispatch> findByTrackingNumber(String trackingNumber);

    /**
     * Lista todos los despachos que se encuentran en un estado determinado.
     *
     * @param status estado del ciclo de vida del despacho por el que filtrar
     * @return {@link Flux} con los despachos en el estado indicado
     */
    Flux<Dispatch> findByStatus(DispatchStatus status);
}
