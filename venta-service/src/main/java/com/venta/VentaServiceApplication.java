package com.venta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VentaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VentaServiceApplication.class, args);
    }
}
