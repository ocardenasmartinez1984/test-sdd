package com.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Clase de arranque del API Gateway del sistema POS.
 *
 * <p>Constituye el único punto de entrada al conjunto de microservicios: recibe
 * las peticiones de los frontends (POS, Ventas y Users Mantenedor) y las enruta
 * hacia los servicios de dominio (auth, stock, venta, despacho) mediante Spring
 * Cloud Gateway.</p>
 *
 * <p>Al estar anotada con {@link org.springframework.cloud.client.discovery.EnableDiscoveryClient}
 * se registra en Eureka y descubre dinámicamente las instancias de destino para
 * el balanceo de carga y el enrutamiento por nombre de servicio.</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
