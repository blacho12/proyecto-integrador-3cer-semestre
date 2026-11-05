package com.grupo7.sgmi.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record CarbonoParametrosEquipoRequest(
        @NotNull(message = "El consumo normal es obligatorio.")
        @DecimalMin(value = "0.0", message = "El consumo normal no puede ser negativo.")
        @Digits(integer = 8, fraction = 2, message = "El consumo admite hasta 8 enteros y 2 decimales.")
        BigDecimal consumoNormalKwh,

        @NotNull(message = "El porcentaje extra es obligatorio.")
        @DecimalMin(value = "0.0", message = "El porcentaje extra no puede ser negativo.")
        @DecimalMax(value = "100.0", message = "El porcentaje extra no puede superar 100.")
        @Digits(integer = 3, fraction = 2, message = "El porcentaje admite hasta 3 enteros y 2 decimales.")
        BigDecimal porcentajeExtra) {
}
