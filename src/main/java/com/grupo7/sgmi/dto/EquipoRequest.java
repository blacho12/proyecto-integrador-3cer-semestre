package com.grupo7.sgmi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de la peticion POST de /api/equipos. */
public record EquipoRequest(
        @NotBlank(message = "El codigo es obligatorio.")
        @Size(max = 30, message = "El codigo no puede superar los 30 caracteres.")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
        String nombre,

        @NotNull(message = "El id del area es obligatorio.")
        Integer idArea) {
}
