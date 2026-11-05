package com.grupo7.sgmi.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grupo7.sgmi.dto.EquipoActualizarRequest;
import com.grupo7.sgmi.dto.EquipoRequest;
import com.grupo7.sgmi.services.EquipoService;

import jakarta.validation.Valid;
import modelo.Equipo;

/** Endpoints del catalogo de equipos. */
@RestController
@RequestMapping("/api/equipos")
public class EquipoController {

    private final EquipoService service;

    public EquipoController(EquipoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Equipo> listar() {
        return service.listar();
    }

    @GetMapping("/{codigo}")
    public Equipo obtener(@PathVariable String codigo) {
        return service.obtener(codigo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Equipo crear(@Valid @RequestBody EquipoRequest peticion) {
        return service.crear(peticion);
    }

    @PutMapping("/{codigo}")
    public Equipo actualizar(@PathVariable String codigo,
                             @Valid @RequestBody EquipoActualizarRequest peticion) {
        return service.actualizar(codigo, peticion);
    }

    @DeleteMapping("/{codigo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String codigo,
                         @RequestParam(defaultValue = "false") boolean eliminarOrdenes) {
        service.eliminar(codigo, eliminarOrdenes);
    }
}
