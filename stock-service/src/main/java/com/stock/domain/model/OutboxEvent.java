package com.stock.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * Entidad de dominio que materializa el patrón <b>Transactional Outbox</b>.
 *
 * <p>Cada documento representa un evento de dominio pendiente de publicar en
 * Kafka; se persiste en la colección {@code outbox_events} de MongoDB dentro de
 * la misma operación que modifica el estado del negocio, garantizando la
 * publicación fiable de eventos (al menos una vez). El
 * {@link com.stock.infrastructure.config.OutboxPublisher} lee periódicamente los
 * registros en estado {@code PENDING}, los envía al topic indicado y actualiza
 * su estado ({@code SENT}/{@code FAILED}) llevando la cuenta de reintentos.</p>
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
