package com.grupo7.sgmi.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo7.sgmi.dto.EquipoActualizarRequest;
import com.grupo7.sgmi.dto.EquipoRequest;
import com.grupo7.sgmi.excepciones.ConflictoDatosException;
import com.grupo7.sgmi.excepciones.RecursoNoEncontradoException;

import datos.AccionDAO;
import datos.EquipoDAO;
import datos.OrdenDAO;
import estructuras.Accion;
import estructuras.Accion.OrdenEnCola;
import estructuras.ColaPrioridad;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/** Reglas de negocio de los equipos; el código sigue siendo su clave de búsqueda AVL. */
@Service
public class EquipoService {

    private final EjecutorBD ejecutor;
    private final EstadoEnMemoria estado;
    private final ObjectMapper objectMapper;

    public EquipoService(EjecutorBD ejecutor, EstadoEnMemoria estado, ObjectMapper objectMapper) {
        this.ejecutor = ejecutor;
        this.estado = estado;
        this.objectMapper = objectMapper;
    }

    public List<Equipo> listar() {
        synchronized (estado) {
            estado.recargar();
            return List.of(estado.getEquipos().inOrden());
        }
    }

    public Equipo obtener(String codigo) {
        synchronized (estado) {
            estado.recargar();
            Equipo equipo = estado.getEquipos().buscar(codigo);
            if (equipo == null) throw noEncontrado(codigo);
            return equipo;
        }
    }

    /** Un código inactivo reactiva el mismo registro; nunca se inserta una segunda identidad. */
    public ResultadoCreacion crear(EquipoRequest peticion) {
        String codigo = peticion.codigo().trim();
        String nombre = peticion.nombre().trim();
        synchronized (estado) {
            estado.recargar();
            ResultadoCreacion resultado = ejecutor.enTransaccion(conexion -> {
                EquipoDAO dao = new EquipoDAO(conexion);
                Equipo existente = dao.consultarPorCodigoParaActualizar(codigo);
                if (existente == null) {
                    return new ResultadoCreacion(dao.insertar(
                            new Equipo(codigo, nombre, peticion.idArea())), false);
                }
                if (existente.isActivo()) {
                    throw new ConflictoDatosException("Ya existe un equipo activo con codigo '" + codigo + "'.");
                }
                Equipo reactivado = dao.reactivar(existente.getId(), nombre, peticion.idArea());
                if (reactivado == null) {
                    throw new ConflictoDatosException("El equipo '" + codigo + "' cambió durante la reactivación.");
                }
                return new ResultadoCreacion(reactivado, true);
            });
            estado.getEquipos().insertar(resultado.equipo());
            return resultado;
        }
    }

    public Equipo actualizar(String codigo, EquipoActualizarRequest peticion) {
        String nombre = peticion.nombre().trim();
        synchronized (estado) {
            estado.recargar();
            Equipo equipo = ejecutor.enTransaccion(conexion -> {
                EquipoDAO dao = new EquipoDAO(conexion);
                Equipo actual = dao.consultarActivoPorCodigoParaActualizar(codigo);
                if (actual == null) throw noEncontrado(codigo);
                actual.setNombre(nombre);
                actual.setIdArea(peticion.idArea());
                if (!dao.actualizar(actual)) throw noEncontrado(codigo);
                return actual;
            });
            estado.getEquipos().modificar(codigo, nombre, peticion.idArea());
            return equipo;
        }
    }

    /** Baja lógica y eliminación atómica únicamente de órdenes que están en la cola pendiente. */
    public void eliminar(String codigo) {
        synchronized (estado) {
            estado.recargar();
            BajaEquipo resultado = ejecutor.enTransaccion(conexion -> {
                AccionDAO acciones = new AccionDAO(conexion, objectMapper);
                acciones.bloquearPila();
                EquipoDAO equipos = new EquipoDAO(conexion);
                Equipo equipo = equipos.consultarActivoPorCodigoParaActualizar(codigo);
                if (equipo == null) throw noEncontrado(codigo);

                OrdenDAO ordenes = new OrdenDAO(conexion);
                List<OrdenMantenimiento> eliminadas =
                        ordenes.consultarPendientesPorEquipoParaActualizar(codigo);
                List<OrdenMantenimiento> todasPendientes =
                        ordenes.consultarPorEstado(Estado.PENDIENTE);
                todasPendientes.sort((a, b) -> a.tienePrioridadSobre(b) ? -1
                        : b.tienePrioridadSobre(a) ? 1 : 0);
                Map<Long, Integer> posiciones = new HashMap<>();
                for (int i = 0; i < todasPendientes.size(); i++) {
                    posiciones.put(todasPendientes.get(i).getId(), i);
                }
                List<OrdenEnCola> snapshot = new ArrayList<>();
                for (OrdenMantenimiento orden : eliminadas) {
                    if (!orden.getEstado().esAbierto()) {
                        throw new ConflictoDatosException(
                                "La orden OM-" + orden.getId() + " ya no está abierta.");
                    }
                    snapshot.add(new OrdenEnCola(orden.copia(), posiciones.get(orden.getId())));
                }
                Accion accion = new Accion(Accion.Tipo.BAJA_EQUIPO, equipo, snapshot);

                for (OrdenMantenimiento orden : eliminadas) {
                    if (!ordenes.eliminarPendiente(orden.getId())) {
                        throw new ConflictoDatosException(
                                "La orden OM-" + orden.getId() + " dejó de estar pendiente durante la baja.");
                    }
                }
                if (!equipos.marcarInactivo(equipo.getId())) {
                    throw new ConflictoDatosException("El equipo '" + codigo + "' cambió durante la baja.");
                }
                acciones.insertar(accion);
                return new BajaEquipo(accion, eliminadas);
            });

            ColaPrioridad cola = estado.getOrdenesPendientes();
            for (OrdenMantenimiento orden : resultado.ordenesEliminadas()) {
                cola.eliminarPorId(orden.getId());
            }
            estado.getEquipos().eliminar(codigo);
            estado.getHistorial().apilar(resultado.accion());
        }
    }

    private RecursoNoEncontradoException noEncontrado(String codigo) {
        return new RecursoNoEncontradoException("No existe el equipo activo con codigo '" + codigo + "'.");
    }

    private record BajaEquipo(Accion accion, List<OrdenMantenimiento> ordenesEliminadas) {
    }

    public record ResultadoCreacion(Equipo equipo, boolean reactivado) {
    }
}
