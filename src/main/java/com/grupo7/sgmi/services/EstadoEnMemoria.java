package com.grupo7.sgmi.services;

import java.util.List;

import org.springframework.stereotype.Component;

import datos.EquipoDAO;
import datos.OrdenDAO;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/**
 * Mantiene las estructuras academicas vivas durante la ejecucion del servidor:
 * el AVL indexa los equipos, la cola de prioridad solo las ordenes PENDIENTE y
 * la pila guarda las acciones para deshacer.
 *
 * PostgreSQL sigue siendo la fuente de verdad. Las estructuras se cargan desde la
 * base en la primera operacion (carga perezosa) y se mantienen sincronizadas en
 * cada alta, modificacion, cierre, cancelacion o borrado realizado por la API.
 *
 * Concurrencia: los servicios acceden a este objeto dentro de synchronized (estado),
 * porque la API atiende peticiones en varios hilos y las estructuras no son thread-safe.
 * La pila es temporal: vive solo mientras el proceso esta en ejecucion.
 */
@Component
public class EstadoEnMemoria {

    private final EjecutorBD ejecutor;
    private final ArbolAVL equipos = new ArbolAVL();
    private final ColaPrioridad ordenesPendientes = new ColaPrioridad();
    private final PilaAcciones historial = new PilaAcciones();

    private boolean cargado;

    public EstadoEnMemoria(EjecutorBD ejecutor) {
        this.ejecutor = ejecutor;
    }

    /** Carga el AVL y la cola desde PostgreSQL una sola vez, en la primera operacion. */
    public synchronized void asegurarCargado() {
        if (cargado) {
            return;
        }
        CargaBD carga = ejecutor.enLectura(conexion -> new CargaBD(
                new EquipoDAO(conexion).consultarTodos(),
                new OrdenDAO(conexion).consultarPorEstado(Estado.PENDIENTE)));

        for (Equipo equipo : carga.equipos()) {
            if (equipos.buscar(equipo.getCodigo()) == null) {
                equipos.insertar(equipo);
            }
        }
        for (OrdenMantenimiento orden : carga.ordenesPendientes()) {
            if (ordenesPendientes.buscarPorId(orden.getId()) == null) {
                ordenesPendientes.encolar(orden);
            }
        }
        cargado = true;
    }

    public ArbolAVL getEquipos() {
        return equipos;
    }

    public ColaPrioridad getOrdenesPendientes() {
        return ordenesPendientes;
    }

    public PilaAcciones getHistorial() {
        return historial;
    }

    /** Resultado de las dos consultas iniciales. */
    private record CargaBD(List<Equipo> equipos, List<OrdenMantenimiento> ordenesPendientes) {
    }
}
