package com.venta.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configuración del manejo de errores de los listeners de Kafka.
 *
 * <p>Define un {@link DefaultErrorHandler} con reintentos y publicación en
 * dead-letter, base del mecanismo de tolerancia a fallos de la SAGA.
 */
@Configuration
public class KafkaErrorHandlerConfig {

    /**
     * Crea el manejador de errores común de los listeners.
     *
     * <p>Reintenta cada registro fallido 3 veces con back-off fijo de 1 segundo y,
     * al agotarse, lo publica en el tópico dead-letter {@code <topic>.dlt}
     * mediante un {@link DeadLetterPublishingRecoverer}.
     *
     * @param kafkaTemplate plantilla usada para publicar los registros al tópico DLT
     * @return manejador de errores configurado para los listeners
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new org.apache.kafka.common.TopicPartition(
                record.topic() + ".dlt", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
