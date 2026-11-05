package com.grupo7.sgmi.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracion CORS de la API.
 *
 * El frontend es una aplicacion estatica independiente y se sirve desde otro
 * origen (por ejemplo, GitHub Pages, Netlify o un servidor local). Sin esta
 * configuracion, el navegador bloquea las peticiones antes de llegar al backend.
 *
 * No se habilitan credenciales: la API es publica para este proyecto y no usa
 * cookies ni cabeceras de autenticacion.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
