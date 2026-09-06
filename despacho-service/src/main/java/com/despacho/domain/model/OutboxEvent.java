package com.despacho.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * Entidad de dominio que materializa un evento del patrón Transactional Outbox.
 *
 * <p>Se persiste en la colección {@code outbox_events} de MongoDB y representa un
 * mensaje pendiente de publicar en Kafka. Almacena el tópico destino, el
 * identificador del agregado (usado como clave del mensaje), la carga útil
 * serializada, el estado del envío ({@code PENDING}, {@code SENT} o
 * {@code FAILED}) y el contador de reintentos. Un publicador periódico lee los
 * eventos en estado pendiente y los emite a Kafka, garantizando la entrega
 * confiable de eventos aun ante fallos transitorios del broker.</p>
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
