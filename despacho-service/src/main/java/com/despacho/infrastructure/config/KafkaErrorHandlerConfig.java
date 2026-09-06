package com.despacho.infrastructure.config;

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
 * Configuración del manejo de errores en el consumo de Kafka.
 *
 * <p>Pertenece a la capa de infraestructura y define la política de reintentos y
 * el enrutamiento de mensajes fallidos hacia tópicos de mensajes muertos
 * (Dead Letter Topics).</p>
 */
@Configuration
public class KafkaErrorHandlerConfig {

    /**
     * Crea el manejador de errores común para los listeners de Kafka.
     *
     * <p>Reintenta el procesamiento hasta 3 veces con un backoff fijo de 1
     * segundo; agotados los reintentos, publica el registro fallido en el tópico
     * de mensajes muertos correspondiente ({@code <topic>.dlt}, en la misma
     * partición) mediante un {@link DeadLetterPublishingRecoverer}.</p>
     *
     * @param kafkaTemplate plantilla usada para publicar en los tópicos DLT
     * @return manejador de errores configurado con reintentos y recuperación DLT
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new org.apache.kafka.common.TopicPartition(
                record.topic() + ".dlt", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
