package com.grupo7.sgmi.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CalculadoraCarbonoTest {

    @Test
    void calculaElEjemploDefinidoEnLaVersionSwing() {
        CalculadoraCarbono.Resultado resultado = CalculadoraCarbono.calcular(
                new BigDecimal("10"), new BigDecimal("8"),
                new BigDecimal("15"), new BigDecimal("0.15"));

        assertEquals(new BigDecimal("12.0000"), resultado.consumoAdicionalKwh());
        assertEquals(new BigDecimal("1.8000"), resultado.emisionesKgCo2());
    }

    @Test
    void redondeaA4DecimalesConHalfUp() {
        CalculadoraCarbono.Resultado resultado = CalculadoraCarbono.calcular(
                new BigDecimal("1"), new BigDecimal("1"),
                new BigDecimal("0.01"), new BigDecimal("0.5"));

        assertEquals(new BigDecimal("0.0001"), resultado.consumoAdicionalKwh());
        assertEquals(new BigDecimal("0.0001"), resultado.emisionesKgCo2());
    }

    @Test
    void rechazaValoresNegativosYPorcentajeMayorA100() {
        assertThrows(IllegalArgumentException.class, () -> CalculadoraCarbono.calcular(
                new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> CalculadoraCarbono.calcular(
                BigDecimal.ONE, new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> CalculadoraCarbono.calcular(
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100.01"), BigDecimal.ONE));
    }
}
