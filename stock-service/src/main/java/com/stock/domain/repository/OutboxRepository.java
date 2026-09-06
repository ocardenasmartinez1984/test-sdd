package com.stock.domain.repository;

import com.stock.domain.model.OutboxEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo de la tabla outbox ({@link OutboxEvent}) sobre MongoDB.
 *
 * <p>Da soporte al patrón Transactional Outbox: extiende
 * {@link ReactiveMongoRepository} para las operaciones CRUD reactivas sobre la
 * colección {@code outbox_events} y expone la consulta que utiliza el publicador
 * para drenar los eventos pendientes en orden de creación.</p>
 */
public interface OutboxRepository extends ReactiveMongoRepository<OutboxEvent, String> {
    /**
     * Recupera los eventos outbox en el estado indicado, ordenados de forma
     * ascendente por su fecha de creación (los más antiguos primero) para
     * respetar el orden de emisión al publicarlos en Kafka.
     *
     * @param status estado por el que filtrar (p. ej. {@code PENDING})
     * @return un {@link Flux} con los eventos coincidentes en orden cronológico
     */
    Flux<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
