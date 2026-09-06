package com.stock.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuración de Kafka (capa de infraestructura) que declara los topics de la
 * SAGA de stock.
 *
 * <p>Define como beans los {@link NewTopic} que Spring Kafka crea
 * automáticamente en el broker al arrancar: los comandos de reserva,
 * compensación y confirmación, y el topic de respuesta de la reserva, todos con
 * 3 particiones y 1 réplica.</p>
 */
@Configuration
public class KafkaConfig {

    /**
     * Declara el topic de comandos de reserva de stock.
     *
     * @return el topic {@code saga.stock.reserve-command}
     */
    @Bean
    public NewTopic stockReserveTopic() {
        return TopicBuilder.name("saga.stock.reserve-command")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declara el topic de comandos de compensación (liberación) de stock.
     *
     * @return el topic {@code saga.stock.compensate-command}
     */
    @Bean
    public NewTopic stockCompensateTopic() {
        return TopicBuilder.name("saga.stock.compensate-command")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declara el topic de comandos de confirmación de despacho de stock.
     *
     * @return el topic {@code saga.stock.confirm-command}
     */
    @Bean
    public NewTopic stockConfirmTopic() {
        return TopicBuilder.name("saga.stock.confirm-command")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declara el topic de respuestas de la reserva de stock hacia la SAGA.
     *
     * @return el topic {@code saga.stock.reserve-reply}
     */
    @Bean
    public NewTopic stockReserveResponseTopic() {
        return TopicBuilder.name("saga.stock.reserve-reply")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
