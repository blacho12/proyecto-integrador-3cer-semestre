package com.grupo7.sgmi.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.grupo7.sgmi.dto.EquipoActualizarRequest;
import com.grupo7.sgmi.dto.EquipoRequest;
import com.grupo7.sgmi.excepciones.ConflictoDatosException;
import com.grupo7.sgmi.excepciones.RecursoNoEncontradoException;

import datos.EquipoDAO;
import datos.OrdenDAO;
import modelo.Equipo;
import modelo.OrdenMantenimiento;

/**
 * Reglas de negocio de los equipos (tabla equipo).
 * El AVL de equipos se mantiene sincronizado con PostgreSQL en cada operacion.
 */
@Service
public class EquipoService {

    private final EjecutorBD ejecutor;
    private final EstadoEnMemoria estado;

    public EquipoService(EjecutorBD ejecutor, EstadoEnMemoria estado) {
        this.ejecutor = ejecutor;
        this.estado = estado;
    }

    /** Equipos ordenados por codigo (recorrido inorden del AVL). */
    public List<Equipo> listar() {
        synchronized (estado) {
            estado.asegurarCargado();
            return List.of(estado.getEquipos().inOrden());
        }
    }

    public Equipo obtener(String codigo) {
        synchronized (estado) {
            estado.asegurarCargado();
            Equipo equipo = estado.getEquipos().buscar(codigo);
            if (equipo == null) {
                throw noEncontrado(codigo);
            }
            return equipo;
        }
    }

    public Equipo crear(EquipoRequest peticion) {
        String codigo = peticion.codigo().trim();
        Equipo equipo = new Equipo(codigo, peticion.nombre().trim(), peticion.idArea());
        synchronized (estado) {
            estado.asegurarCargado();
            ejecutor.enTransaccion(conexion -> {
                new EquipoDAO(conexion).insertar(equipo);
                return null;
            });
            estado.getEquipos().insertar(equipo);
        }
        return equipo;
    }

    public Equipo actualizar(String codigo, EquipoActualizarRequest peticion) {
        String nombre = peticion.nombre().trim();
        synchronized (estado) {
            estado.asegurarCargado();
            Equipo equipo = ejecutor.enTransaccion(conexion -> {
                EquipoDAO dao = new EquipoDAO(conexion);
                Equipo actual = dao.consultarPorCodigo(codigo);
                if (actual == null) {
                    throw noEncontrado(codigo);
                }
                actual.setNombre(nombre);
                actual.setIdArea(peticion.idArea());
                if (!dao.actualizar(actual)) {
                    throw noEncontrado(codigo);
                }
                return actual;
            });
            if (estado.getEquipos().buscar(codigo) == null) {
                estado.getEquipos().insertar(equipo);
            } else {
                estado.getEquipos().modificar(codigo, nombre, peticion.idArea());
            }
            return equipo;
        }
    }

    /**
     * Elimina un equipo y, opcionalmente, las ordenes que lo referencian.
     * La clave foranea usa ON DELETE RESTRICT, por lo que sin eliminarOrdenes=true
     * PostgreSQL rechaza el borrado y se devuelve un conflicto.
     */
    public void eliminar(String codigo, boolean eliminarOrdenes) {
        synchronized (estado) {
            estado.asegurarCargado();
            List<OrdenMantenimiento> asociadas = ejecutor.enTransaccion(conexion -> {
                EquipoDAO equipos = new EquipoDAO(conexion);
                OrdenDAO ordenes = new OrdenDAO(conexion);

                if (equipos.consultarPorCodigo(codigo) == null) {
                    throw noEncontrado(codigo);
                }
                List<OrdenMantenimiento> lista = ordenes.consultarPorEquipo(codigo);
                if (!lista.isEmpty() && !eliminarOrdenes) {
                    throw new ConflictoDatosException(
                            "El equipo '" + codigo + "' tiene " + lista.size()
                            + (lista.size() == 1 ? " orden asociada" : " ordenes asociadas")
                            + "; PostgreSQL no permite borrarlo (ON DELETE RESTRICT). "
                            + "Repita la operacion con eliminarOrdenes=true para borrar tambien esas ordenes.");
                }
                for (OrdenMantenimiento orden : lista) {
                    if (!ordenes.eliminar(orden.getId())) {
                        throw new ConflictoDatosException(
                                "La orden OM-" + orden.getId() + " ya no existe en PostgreSQL.");
                    }
                }
                if (!equipos.eliminar(codigo)) {
                    throw noEncontrado(codigo);
                }
                return lista;
            });

            for (OrdenMantenimiento orden : asociadas) {
                estado.getOrdenesPendientes().eliminarPorId(orden.getId());
            }
            estado.getEquipos().eliminar(codigo);
        }
    }

    private RecursoNoEncontradoException noEncontrado(String codigo) {
        return new RecursoNoEncontradoException("No existe el equipo con codigo '" + codigo + "'.");
    }
}
