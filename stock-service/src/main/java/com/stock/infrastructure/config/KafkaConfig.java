package com.stock.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic stockReserveTopic() {
        return TopicBuilder.name("saga.stock.reserve-command")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockCompensateTopic() {
        return TopicBuilder.name("saga.stock.compensate-command")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockConfirmTopic() {
        return TopicBuilder.name("saga.stock.confirm-command")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockReserveResponseTopic() {
        return TopicBuilder.name("saga.stock.reserve-reply")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
