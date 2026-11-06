package com.grupo7.sgmi.services;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import datos.AccionDAO;
import datos.EquipoDAO;
import datos.OrdenDAO;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/** Estructuras académicas en memoria, reconstruidas desde PostgreSQL al primer uso. */
@Component
public class EstadoEnMemoria implements ApplicationRunner {

    private final EjecutorBD ejecutor;
    private final ObjectMapper objectMapper;
    private final ArbolAVL equipos = new ArbolAVL();
    private final ColaPrioridad ordenesPendientes = new ColaPrioridad();
    private final PilaAcciones historial = new PilaAcciones();

    private boolean cargado;

    public EstadoEnMemoria(EjecutorBD ejecutor, ObjectMapper objectMapper) {
        this.ejecutor = ejecutor;
        this.objectMapper = objectMapper;
    }

    public synchronized void asegurarCargado() {
        if (cargado) return;
        CargaBD carga = ejecutor.enLectura(conexion -> new CargaBD(
                new EquipoDAO(conexion).consultarTodos(),
                new OrdenDAO(conexion).consultarPorEstado(Estado.PENDIENTE),
                new AccionDAO(conexion, objectMapper).consultarActivas()));

        equipos.limpiar();
        ordenesPendientes.limpiar();
        historial.limpiar();
        for (Equipo equipo : carga.equipos()) equipos.insertar(equipo);
        for (OrdenMantenimiento orden : carga.ordenesPendientes()) ordenesPendientes.encolar(orden);
        for (estructuras.Accion accion : carga.acciones()) historial.apilar(accion);
        cargado = true;
    }

    @Override
    public void run(ApplicationArguments args) {
        asegurarCargado();
    }

    public synchronized void recargar() {
        cargado = false;
        asegurarCargado();
    }

    public ArbolAVL getEquipos() { return equipos; }
    public ColaPrioridad getOrdenesPendientes() { return ordenesPendientes; }
    public PilaAcciones getHistorial() { return historial; }

    private record CargaBD(List<Equipo> equipos, List<OrdenMantenimiento> ordenesPendientes,
                           List<estructuras.Accion> acciones) {
    }
}
