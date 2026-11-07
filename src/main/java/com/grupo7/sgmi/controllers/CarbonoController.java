package com.grupo7.sgmi.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grupo7.sgmi.dto.CarbonoConfiguracionDTO;
import com.grupo7.sgmi.dto.CarbonoEmisionesAreaDTO;
import com.grupo7.sgmi.dto.CarbonoEmisionesEquipoDTO;
import com.grupo7.sgmi.dto.CarbonoFactorRequest;
import com.grupo7.sgmi.dto.CarbonoParametrosEquipoRequest;
import com.grupo7.sgmi.dto.CarbonoRegistroDTO;
import com.grupo7.sgmi.dto.CarbonoRegistroRequest;
import com.grupo7.sgmi.dto.CarbonoResumenDTO;
import com.grupo7.sgmi.services.CarbonoService;

import jakarta.validation.Valid;
import modelo.Equipo;

/** API REST documentada para la estimacion y consulta de emisiones adicionales. */
@RestController
@RequestMapping("/api/carbono")
public class CarbonoController {

    private final CarbonoService service;

    public CarbonoController(CarbonoService service) {
        this.service = service;
    }

    @GetMapping("/resumen")
    public CarbonoResumenDTO resumen() {
        return service.resumen();
    }

    @GetMapping("/registros")
    public List<CarbonoRegistroDTO> registros() {
        return service.registros();
    }

    @PostMapping("/registros")
    @ResponseStatus(HttpStatus.CREATED)
    public CarbonoRegistroDTO registrar(@Valid @RequestBody CarbonoRegistroRequest peticion) {
        return service.registrar(peticion);
    }

    @GetMapping("/configuracion")
    public CarbonoConfiguracionDTO configuracion() {
        return service.consultarConfiguracion();
    }

    @PutMapping("/configuracion")
    public CarbonoConfiguracionDTO actualizarFactor(@Valid @RequestBody CarbonoFactorRequest peticion) {
        return service.actualizarFactor(peticion);
    }

    @PutMapping("/equipos/{codigo}/parametros")
    public Equipo actualizarParametros(@PathVariable String codigo,
                                       @Valid @RequestBody CarbonoParametrosEquipoRequest peticion) {
        return service.actualizarParametros(codigo, peticion);
    }
}
