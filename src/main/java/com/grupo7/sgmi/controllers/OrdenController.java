package com.grupo7.sgmi.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grupo7.sgmi.dto.AccionDTO;
import com.grupo7.sgmi.dto.OrdenActualizarRequest;
import com.grupo7.sgmi.dto.OrdenRequest;
import com.grupo7.sgmi.services.OrdenService;

import jakarta.validation.Valid;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/** Endpoints de las ordenes de mantenimiento, la cola de prioridad y el historial. */
@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    private final OrdenService service;

    public OrdenController(OrdenService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrdenMantenimiento> listar(
            @RequestParam(required = false) Estado estado,
            @RequestParam(required = false) String codigoEquipo) {
        return service.listar(estado, codigoEquipo);
    }

    /** Ordenes pendientes en orden de atencion (la primera es la mas critica). */
    @GetMapping("/cola")
    public List<OrdenMantenimiento> cola() {
        return service.cola();
    }

    /** Acciones disponibles para deshacer, de la mas reciente a la mas antigua. */
    @GetMapping("/historial")
    public List<AccionDTO> historial() {
        return service.historial();
    }

    @GetMapping("/{id}")
    public OrdenMantenimiento obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenMantenimiento crear(@Valid @RequestBody OrdenRequest peticion) {
        return service.crear(peticion);
    }

    @PutMapping("/{id}")
    public OrdenMantenimiento modificar(@PathVariable long id,
                                        @Valid @RequestBody OrdenActualizarRequest peticion) {
        return service.modificar(id, peticion);
    }

    @PostMapping("/{id}/cerrar")
    public OrdenMantenimiento cerrar(@PathVariable long id) {
        return service.cerrar(id);
    }

    @PostMapping("/{id}/cancelar")
    public OrdenMantenimiento cancelar(@PathVariable long id) {
        return service.cancelar(id);
    }

    /** Cierra la orden de mayor prioridad de la cola. */
    @PostMapping("/atender-siguiente")
    public OrdenMantenimiento atenderSiguiente() {
        return service.atenderSiguiente();
    }

    /** Deshace la ultima accion registrada (pila LIFO). */
    @PostMapping("/historial/deshacer")
    public AccionDTO deshacer() {
        return service.deshacer();
    }
}
