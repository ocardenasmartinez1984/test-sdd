package com.venta.domain.repository;

import com.venta.domain.model.OutboxEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo de eventos del outbox transaccional sobre MongoDB.
 *
 * <p>Lo usa el {@code OutboxPublisher} para recuperar los eventos pendientes y
 * relayarlos a Kafka en orden de creación.
 */
public interface OutboxRepository extends ReactiveMongoRepository<OutboxEvent, String> {
    /**
     * Devuelve los eventos en el estado dado ordenados por fecha de creación
     * ascendente, para publicarlos preservando el orden.
     *
     * @param status estado por el que filtrar (normalmente PENDING)
     * @return flujo de eventos ordenados por antigüedad
     */
    Flux<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
