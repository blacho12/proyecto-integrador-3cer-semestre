package com.grupo7.sgmi.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record CarbonoFactorRequest(
        @NotNull(message = "El factor de emision es obligatorio.")
        @DecimalMin(value = "0.0", message = "El factor de emision no puede ser negativo.")
        @Digits(integer = 6, fraction = 4, message = "El factor admite hasta 6 enteros y 4 decimales.")
        BigDecimal factorEmision) {
}
