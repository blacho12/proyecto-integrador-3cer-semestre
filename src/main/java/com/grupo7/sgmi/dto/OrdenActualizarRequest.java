package com.grupo7.sgmi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de la peticion PUT de /api/ordenes/{id}. */
public record OrdenActualizarRequest(
        @NotBlank(message = "El tipo es obligatorio (PREVENTIVO o CORRECTIVO).")
        String tipo,

        @NotBlank(message = "La descripcion es obligatoria.")
        String descripcion,

        @NotNull(message = "El impacto en produccion es obligatorio.")
        @Min(value = 1, message = "El impacto debe estar entre 1 y 3.")
        @Max(value = 3, message = "El impacto debe estar entre 1 y 3.")
        Integer impacto,

        @NotNull(message = "El riesgo de seguridad es obligatorio.")
        @Min(value = 1, message = "El riesgo debe estar entre 1 y 3.")
        @Max(value = 3, message = "El riesgo debe estar entre 1 y 3.")
        Integer riesgoSeguridad,

        @NotNull(message = "Debe indicar si hay respaldo (true o false).")
        Boolean hayRespaldo) {
}
