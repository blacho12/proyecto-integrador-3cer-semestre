package com.grupo7.sgmi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de la peticion PUT de /api/equipos/{codigo}. El codigo va en la ruta y no cambia. */
public record EquipoActualizarRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
        String nombre,

        @NotNull(message = "El id del area es obligatorio.")
        Integer idArea) {
}
