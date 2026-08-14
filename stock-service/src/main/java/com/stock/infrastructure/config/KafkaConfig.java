package com.stock.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic stockReserveTopic() {
        return TopicBuilder.name("stock-reserve")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockCompensateTopic() {
        return TopicBuilder.name("stock-compensate")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockReserveResponseTopic() {
        return TopicBuilder.name("stock-reserve-response")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
