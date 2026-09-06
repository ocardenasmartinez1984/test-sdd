package com.venta.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * Evento pendiente de publicación siguiendo el patrón <b>Transactional
 * Outbox</b>.
 *
 * <p>En lugar de publicar directamente a Kafka al modificar el estado, se guarda
 * el evento en la colección {@code outbox_events} de MongoDB dentro de la misma
 * operación de negocio; luego el {@code OutboxPublisher} lo relaya a Kafka de
 * forma asíncrona y fiable, marcándolo como {@code SENT} o {@code FAILED}. Esto
 * garantiza la entrega al menos una vez sin acoplar la persistencia con el envío.
 * Lombok genera constructores, getters y setters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_events")
public class OutboxEvent {
    @Id
    private String id;
    private String aggregateId;
    private String eventType;
    private String topic;
    private String payload;
    private String status; // PENDING, SENT, FAILED
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
}
