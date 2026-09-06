package com.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Clase de arranque del servidor de descubrimiento de servicios (Eureka Server).
 *
 * <p>Actúa como el registro central (service discovery) de la arquitectura de
 * microservicios del sistema POS: cada servicio (api-gateway, auth, stock,
 * venta, despacho) se registra aquí al iniciarse, y el resto de componentes
 * consulta este registro para localizar y balancear las instancias disponibles.</p>
 *
 * <p>La anotación {@link org.springframework.cloud.netflix.eureka.server.EnableEurekaServer}
 * habilita el rol de servidor Eureka en esta aplicación.</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
