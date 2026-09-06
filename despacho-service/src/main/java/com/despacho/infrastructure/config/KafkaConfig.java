package com.despacho.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Kafka para el servicio de despacho.
 *
 * <p>Pertenece a la capa de infraestructura y declara los beans necesarios para
 * consumir y producir mensajes: la {@code ConsumerFactory}/{@code
 * ProducerFactory}, la {@code ContainerFactory} de listeners y el {@code
 * KafkaTemplate}. Configura la (de)serialización JSON de los valores y toma la
 * dirección de los brokers de la propiedad
 * {@code spring.kafka.bootstrap-servers}.</p>
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Crea la fábrica de consumidores Kafka.
     *
     * <p>Configura los brokers, el {@code group.id} {@code despacho-group}, el
     * deserializador de clave ({@code String}) y el de valor ({@code
     * JsonDeserializer}) confiando en todos los paquetes, sin usar cabeceras de
     * tipo y deserializando por defecto a {@code java.util.HashMap}.</p>
     *
     * @return fábrica de consumidores configurada para mensajes de tipo mapa
     */
    @Bean
    public ConsumerFactory<String, Map<String, Object>> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "despacho-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.HashMap");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Crea la fábrica de contenedores de listeners Kafka concurrentes.
     *
     * <p>Enlaza los {@code @KafkaListener} con la {@link #consumerFactory()}
     * definida para recibir mensajes deserializados como mapa.</p>
     *
     * @return fábrica de contenedores de listeners configurada
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    /**
     * Crea la fábrica de productores Kafka.
     *
     * <p>Configura los brokers, el serializador de clave ({@code String}) y el
     * de valor ({@code JsonSerializer}) para emitir mensajes en formato JSON.</p>
     *
     * @return fábrica de productores configurada
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Crea el {@link KafkaTemplate} usado por el servicio para publicar eventos.
     *
     * @return plantilla de Kafka basada en la {@link #producerFactory()}
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
