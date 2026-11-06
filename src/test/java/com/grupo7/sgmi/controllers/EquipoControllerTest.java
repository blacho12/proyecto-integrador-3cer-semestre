package com.grupo7.sgmi.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.grupo7.sgmi.config.WebConfig;
import com.grupo7.sgmi.excepciones.ManejadorGlobalExcepciones;
import com.grupo7.sgmi.services.EquipoService;
import modelo.Equipo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

@WebMvcTest(EquipoController.class)
@Import({WebConfig.class, ManejadorGlobalExcepciones.class})
class EquipoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipoService service;

    @Test
    void devuelveCreatedAlCrearUnEquipo() throws Exception {
        when(service.crear(any())).thenReturn(new EquipoService.ResultadoCreacion(equipo(), false));

        mockMvc.perform(post("/api/equipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"EQ-1","nombre":"Bomba","idArea":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void devuelveOkAlReactivarUnEquipoExistente() throws Exception {
        when(service.crear(any())).thenReturn(new EquipoService.ResultadoCreacion(equipo(), true));

        mockMvc.perform(post("/api/equipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"EQ-1","nombre":"Bomba","idArea":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void aceptaLaBajaSinParametroDeBorradoEnCascada() throws Exception {
        mockMvc.perform(delete("/api/equipos/EQ-1"))
                .andExpect(status().isNoContent());

        verify(service).eliminar("EQ-1");
    }

    private Equipo equipo() {
        return new Equipo(7, "EQ-1", "Bomba", 1, BigDecimal.ZERO, BigDecimal.ZERO, true);
    }
}
