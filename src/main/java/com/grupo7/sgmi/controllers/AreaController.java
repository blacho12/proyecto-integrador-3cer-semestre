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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grupo7.sgmi.dto.AreaRequest;
import com.grupo7.sgmi.services.AreaService;

import jakarta.validation.Valid;
import modelo.Area;

/** Endpoints de las areas de la planta. */
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private final AreaService service;

    public AreaController(AreaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Area> listar() {
        return service.listar();
    }

    @GetMapping("/{idArea}")
    public Area obtener(@PathVariable int idArea) {
        return service.obtener(idArea);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Area crear(@Valid @RequestBody AreaRequest peticion) {
        return service.crear(peticion);
    }

    @PutMapping("/{idArea}")
    public Area actualizar(@PathVariable int idArea, @Valid @RequestBody AreaRequest peticion) {
        return service.actualizar(idArea, peticion);
    }

    @DeleteMapping("/{idArea}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int idArea) {
        service.eliminar(idArea);
    }
}
