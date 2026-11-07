package com.grupo7.sgmi.dto;

import java.time.LocalDateTime;

/** Respuesta del endpoint de salud usado por Render. */
public record EstadoDTO(
        String estado,
        String baseDatos,
        LocalDateTime timestamp) {
}
