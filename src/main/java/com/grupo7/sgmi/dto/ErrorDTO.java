package com.grupo7.sgmi.dto;

import java.time.LocalDateTime;

/** Formato uniforme de error para toda la API. */
public record ErrorDTO(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        String ruta) {
}
