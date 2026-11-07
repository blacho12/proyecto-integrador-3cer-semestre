package com.grupo7.sgmi.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CarbonoRegistroRequest(
        @NotBlank(message = "El codigo del equipo es obligatorio.")
        @Size(max = 30, message = "El codigo del equipo no puede superar los 30 caracteres.")
        String codigoEquipo,

        @NotNull(message = "Las horas en mal estado son obligatorias.")
        @DecimalMin(value = "0.0", message = "Las horas en mal estado no pueden ser negativas.")
        @Digits(integer = 8, fraction = 2, message = "Las horas admiten hasta 8 enteros y 2 decimales.")
        BigDecimal horasMalEstado) {
}
