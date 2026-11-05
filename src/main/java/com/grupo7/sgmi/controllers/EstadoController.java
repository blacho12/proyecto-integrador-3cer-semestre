package com.grupo7.sgmi.controllers;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grupo7.sgmi.dto.EstadoDTO;
import com.grupo7.sgmi.services.EjecutorBD;

/** Endpoint de salud usado como Health Check por Render. */
@RestController
@RequestMapping("/api/estado")
public class EstadoController {

    private final EjecutorBD ejecutor;

    public EstadoController(EjecutorBD ejecutor) {
        this.ejecutor = ejecutor;
    }

    @GetMapping
    public ResponseEntity<EstadoDTO> estado() {
        try {
            boolean conectada = ejecutor.enLectura(conexion -> conexion.isValid(2));
            if (conectada) {
                return ResponseEntity.ok(new EstadoDTO("ok", "conectada", LocalDateTime.now().withNano(0)));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new EstadoDTO("degradado", "sin conexion", LocalDateTime.now().withNano(0)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new EstadoDTO("degradado", "sin conexion", LocalDateTime.now().withNano(0)));
        }
    }
}
