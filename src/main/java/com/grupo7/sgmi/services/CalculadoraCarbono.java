package com.grupo7.sgmi.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calcula emisiones adicionales usando las reglas de la version Swing original. */
public final class CalculadoraCarbono {

    public static final int ESCALA = 4;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private CalculadoraCarbono() {
    }

    public static Resultado calcular(BigDecimal consumoNormalKwh, BigDecimal horasMalEstado,
                                     BigDecimal porcentajeExtra, BigDecimal factorEmision) {
        validarNoNegativo("consumo normal", consumoNormalKwh);
        validarNoNegativo("horas en mal estado", horasMalEstado);
        validarNoNegativo("porcentaje extra", porcentajeExtra);
        validarNoNegativo("factor de emision", factorEmision);
        if (porcentajeExtra.compareTo(CIEN) > 0) {
            throw new IllegalArgumentException("El porcentaje extra no puede superar 100.");
        }

        BigDecimal consumoAdicional = consumoNormalKwh.multiply(horasMalEstado)
                .multiply(porcentajeExtra).divide(CIEN)
                .setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal emisiones = consumoAdicional.multiply(factorEmision)
                .setScale(ESCALA, RoundingMode.HALF_UP);
        return new Resultado(consumoAdicional, emisiones);
    }

    private static void validarNoNegativo(String campo, BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El valor de " + campo + " es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("El valor de " + campo + " no puede ser negativo.");
        }
    }

    public record Resultado(BigDecimal consumoAdicionalKwh, BigDecimal emisionesKgCo2) {
    }
}
