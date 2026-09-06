package com.despacho.domain.repository;

import com.despacho.domain.model.OutboxEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo de acceso a datos para la entidad {@link OutboxEvent}.
 *
 * <p>Pertenece a la capa de dominio y soporta el patrón Transactional Outbox,
 * abstrayendo sobre MongoDB (Spring Data Reactive) la lectura de los eventos
 * pendientes de publicar en Kafka.</p>
 */
public interface OutboxRepository extends ReactiveMongoRepository<OutboxEvent, String> {

    /**
     * Recupera los eventos del outbox con el estado indicado, ordenados de forma
     * ascendente por fecha de creación (los más antiguos primero) para
     * preservar el orden de publicación.
     *
     * @param status estado por el que filtrar (por ejemplo {@code PENDING})
     * @return {@link Flux} con los eventos coincidentes en orden cronológico
     */
    Flux<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
