package com.stock.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuración de Redis reactivo (capa de infraestructura).
 *
 * <p>Define el {@link ReactiveRedisTemplate} usado por el
 * {@link ProductCacheService} para la caché de productos, configurando la
 * serialización de claves como texto y de valores como JSON.</p>
 */
@Configuration
public class RedisCacheConfig {

    /**
     * Crea el template reactivo de Redis con serialización JSON de valores.
     *
     * <p>Configura un {@link ObjectMapper} con soporte para tipos de fecha/hora
     * de Java 8 ({@link JavaTimeModule}) y typing por defecto (para preservar el
     * tipo concreto de los objetos deserializados), y aplica un
     * {@link StringRedisSerializer} a las claves y un
     * {@link Jackson2JsonRedisSerializer} a valores y hash-values.</p>
     *
     * @param connectionFactory fábrica de conexiones reactivas a Redis inyectada
     *                          por Spring
     * @return el {@link ReactiveRedisTemplate} configurado
     */
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, Object.class);

        RedisSerializationContext<String, Object> context =
                RedisSerializationContext.<String, Object>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
