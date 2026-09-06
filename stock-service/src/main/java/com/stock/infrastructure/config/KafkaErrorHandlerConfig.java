package com.stock.infrastructure.config;

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
 * Configuración del manejo de errores del consumidor Kafka (infraestructura).
 *
 * <p>Registra un manejador de errores común que reintenta el procesamiento de
 * los mensajes fallidos y, tras agotar los reintentos, los reencamina a un topic
 * de mensajes muertos (Dead Letter Topic).</p>
 */
@Configuration
public class KafkaErrorHandlerConfig {

    /**
     * Crea el manejador de errores con reintentos y publicación en DLT.
     *
     * <p>Usa un {@link DeadLetterPublishingRecoverer} que envía los registros no
     * recuperables al topic {@code <topic-original>.dlt} conservando la
     * partición, dentro de un {@link DefaultErrorHandler} configurado con un
     * backoff fijo de 1 segundo y hasta 3 reintentos.</p>
     *
     * @param kafkaTemplate template usado para publicar los mensajes al DLT
     * @return el {@link CommonErrorHandler} configurado
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new org.apache.kafka.common.TopicPartition(
                record.topic() + ".dlt", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
