package com.grupo7.sgmi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la API REST del Sistema de Gestion de Mantenimiento Industrial.
 *
 * Los paquetes academicos modelo/, estructuras/ y datos/ se conservan sin cambios;
 * esta clase solo habilita el arranque de Spring Boot y el escaneo de componentes
 * del paquete com.grupo7.sgmi (controllers, services, config y excepciones).
 */
@SpringBootApplication
public class SgmiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SgmiApplication.class, args);
    }
}
