package com.grupo7.sgmi.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grupo7.sgmi.dto.EstadisticasDTO;
import com.grupo7.sgmi.services.EstadisticasService;

/** Endpoints de indicadores agregados. */
@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasController {

    private final EstadisticasService service;

    public EstadisticasController(EstadisticasService service) {
        this.service = service;
    }

    @GetMapping
    public EstadisticasDTO calcular() {
        return service.calcular();
    }
}
