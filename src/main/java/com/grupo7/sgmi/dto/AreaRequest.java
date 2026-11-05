package com.grupo7.sgmi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de las peticiones POST y PUT de /api/areas. */
public record AreaRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
        String nombre,

        @Size(max = 250, message = "La descripcion no puede superar los 250 caracteres.")
        String descripcion) {
}
