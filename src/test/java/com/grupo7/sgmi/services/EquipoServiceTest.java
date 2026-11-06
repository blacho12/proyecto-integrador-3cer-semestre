package com.grupo7.sgmi.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo7.sgmi.excepciones.ErrorBaseDatosException;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Equipo;
import org.junit.jupiter.api.Test;

class EquipoServiceTest {

    @Test
    void unaFallaTransaccionalNoCambiaElAvlNiLaPila() {
        EjecutorBD ejecutor = mock(EjecutorBD.class);
        EstadoEnMemoria estado = mock(EstadoEnMemoria.class);
        ArbolAVL equipos = new ArbolAVL();
        ColaPrioridad cola = new ColaPrioridad();
        PilaAcciones historial = new PilaAcciones();
        Equipo equipo = new Equipo(7, "EQ-7", "Bomba", 1,
                BigDecimal.ZERO, BigDecimal.ZERO, true);
        equipos.insertar(equipo);
        when(estado.getEquipos()).thenReturn(equipos);
        when(estado.getOrdenesPendientes()).thenReturn(cola);
        when(estado.getHistorial()).thenReturn(historial);
        doThrow(new ErrorBaseDatosException("Fallo de prueba"))
                .when(ejecutor).enTransaccion(any());

        EquipoService service = new EquipoService(ejecutor, estado, new ObjectMapper());

        assertThrows(ErrorBaseDatosException.class, () -> service.eliminar("EQ-7"));

        assertSame(equipo, equipos.buscar("EQ-7"));
        assertEquals(0, cola.tamano());
        assertEquals(0, historial.tamano());
    }
}
