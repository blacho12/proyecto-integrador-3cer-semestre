package com.grupo7.sgmi.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.grupo7.sgmi.config.WebConfig;
import com.grupo7.sgmi.dto.CarbonoRegistroDTO;
import com.grupo7.sgmi.dto.CarbonoRegistroRequest;
import com.grupo7.sgmi.dto.CarbonoResumenDTO;
import com.grupo7.sgmi.excepciones.ManejadorGlobalExcepciones;
import com.grupo7.sgmi.services.CarbonoService;

@WebMvcTest(CarbonoController.class)
@Import({WebConfig.class, ManejadorGlobalExcepciones.class})
class CarbonoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarbonoService service;

    @Test
    void exponeElContratoDelResumen() throws Exception {
        when(service.resumen()).thenReturn(new CarbonoResumenDTO(
                new BigDecimal("1.8000"), 1, 1, new BigDecimal("0.1500"),
                List.of(), List.of(), List.of()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/carbono/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmisionesKgCo2").value(1.8))
                .andExpect(jsonPath("$.equiposConEmisiones").value(1))
                .andExpect(jsonPath("$.factorEmision").value(0.15));
    }

    @Test
    void validaLasHorasAntesDeLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/carbono/registros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoEquipo":"EQ-1","horasMalEstado":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString(
                        "horasMalEstado")));
    }

    @Test
    void rechazaPorcentajeDeEquipoMayorA100() throws Exception {
        mockMvc.perform(put("/api/carbono/equipos/EQ-1/parametros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumoNormalKwh":10,"porcentajeExtra":100.01}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString(
                        "porcentajeExtra")));
    }

    @Test
    void rechazaUnFactorNegativo() throws Exception {
        mockMvc.perform(put("/api/carbono/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"factorEmision":-0.1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString(
                        "factorEmision")));
    }

    @Test
    void registraUnaEmisionYDevuelveCreated() throws Exception {
        when(service.registrar(any(CarbonoRegistroRequest.class))).thenReturn(new CarbonoRegistroDTO(
                7, "EQ-1", "Equipo 1", "Produccion", new BigDecimal("8.00"),
                new BigDecimal("12.0000"), new BigDecimal("1.8000"),
                new BigDecimal("0.1500"), LocalDateTime.of(2026, 11, 5, 12, 0)));

        mockMvc.perform(post("/api/carbono/registros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoEquipo":"EQ-1","horasMalEstado":8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idRegistro").value(7))
                .andExpect(jsonPath("$.consumoAdicionalKwh").value(12))
                .andExpect(jsonPath("$.emisionesKgCo2").value(1.8));
    }

    @Test
    void admiteSolicitudesCorsDeOrigenConfigurado() throws Exception {
        mockMvc.perform(options("/api/carbono/resumen")
                        .header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }
}
