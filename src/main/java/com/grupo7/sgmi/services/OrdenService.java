package com.grupo7.sgmi.services;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo7.sgmi.dto.AccionDTO;
import com.grupo7.sgmi.dto.OrdenActualizarRequest;
import com.grupo7.sgmi.dto.OrdenRequest;
import com.grupo7.sgmi.excepciones.ConflictoDatosException;
import com.grupo7.sgmi.excepciones.DatosInvalidosException;
import com.grupo7.sgmi.excepciones.RecursoNoEncontradoException;

import datos.AccionDAO;
import datos.EquipoDAO;
import datos.OrdenDAO;
import estructuras.Accion;
import estructuras.Accion.OrdenEnCola;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/** Reglas de negocio de las órdenes, la cola de prioridad y la pila persistente. */
@Service
public class OrdenService {

    private final EjecutorBD ejecutor;
    private final EstadoEnMemoria estado;
    private final ObjectMapper objectMapper;

    public OrdenService(EjecutorBD ejecutor, EstadoEnMemoria estado, ObjectMapper objectMapper) {
        this.ejecutor = ejecutor;
        this.estado = estado;
        this.objectMapper = objectMapper;
    }

    public List<OrdenMantenimiento> listar(Estado estadoOrden, String codigoEquipo) {
        String codigo = (codigoEquipo == null || codigoEquipo.isBlank()) ? null : codigoEquipo.trim();
        return ejecutor.enLectura(conexion -> {
            OrdenDAO dao = new OrdenDAO(conexion);
            if (estadoOrden != null && codigo != null) {
                return dao.consultarPorEstado(estadoOrden).stream()
                        .filter(orden -> codigo.equals(orden.getCodigoEquipo())).toList();
            }
            if (estadoOrden != null) return dao.consultarPorEstado(estadoOrden);
            if (codigo != null) return dao.consultarPorEquipo(codigo);
            return dao.consultarTodas();
        });
    }

    public OrdenMantenimiento obtener(long id) {
        OrdenMantenimiento orden = ejecutor.enLectura(conexion -> new OrdenDAO(conexion).consultarPorId(id));
        if (orden == null) throw noEncontrada(id);
        return orden;
    }

    public List<OrdenMantenimiento> cola() {
        synchronized (estado) {
            estado.recargar();
            return List.of(estado.getOrdenesPendientes().recorridoPorPrioridad());
        }
    }

    public OrdenMantenimiento crear(OrdenRequest peticion) {
        String codigo = peticion.codigoEquipo().trim();
        String tipo = peticion.tipo().trim().toUpperCase(Locale.ROOT);
        String descripcion = peticion.descripcion().trim();
        validarOrden(tipo, descripcion, peticion.impacto(), peticion.riesgoSeguridad());

        synchronized (estado) {
            estado.recargar();
            Creacion resultado = ejecutor.enTransaccion(conexion -> {
                AccionDAO acciones = new AccionDAO(conexion, objectMapper);
                acciones.bloquearPila();
                Equipo equipo = new EquipoDAO(conexion).consultarActivoPorCodigoParaActualizar(codigo);
                if (equipo == null) {
                    throw new RecursoNoEncontradoException(
                            "El equipo '" + codigo + "' no existe o está inactivo.");
                }
                OrdenMantenimiento nueva = new OrdenMantenimiento(
                        0, 0, equipo.getId(), codigo, tipo, descripcion, peticion.impacto(),
                        peticion.riesgoSeguridad(), peticion.hayRespaldo(), Estado.PENDIENTE, null, null);
                OrdenMantenimiento creada = new OrdenDAO(conexion).insertar(nueva);
                Accion accion = acciones.insertar(new Accion(Accion.Tipo.CREAR, creada.copia()));
                return new Creacion(creada, accion);
            });
            estado.getOrdenesPendientes().encolar(resultado.orden());
            estado.getHistorial().apilar(resultado.accion());
            return resultado.orden();
        }
    }

    public OrdenMantenimiento modificar(long id, OrdenActualizarRequest peticion) {
        String tipo = peticion.tipo().trim().toUpperCase(Locale.ROOT);
        String descripcion = peticion.descripcion().trim();
        validarOrden(tipo, descripcion, peticion.impacto(), peticion.riesgoSeguridad());

        synchronized (estado) {
            estado.recargar();
            Modificacion resultado = ejecutor.enTransaccion(conexion -> {
                AccionDAO acciones = new AccionDAO(conexion, objectMapper);
                acciones.bloquearPila();
                OrdenDAO dao = new OrdenDAO(conexion);
                OrdenMantenimiento orden = dao.consultarPorIdParaActualizar(id);
                if (orden == null || !orden.getEstado().esAbierto()) throw noEncontrada(id);
                OrdenMantenimiento previa = orden.copia();
                orden.setTipo(tipo);
                orden.setDescripcion(descripcion);
                orden.setCriterios(peticion.impacto(), peticion.riesgoSeguridad(), peticion.hayRespaldo());
                if (!dao.actualizar(orden)) throw noEncontrada(id);
                Accion accion = acciones.insertar(new Accion(Accion.Tipo.MODIFICAR, previa));
                return new Modificacion(orden, accion);
            });
            estado.getOrdenesPendientes().eliminarPorId(id);
            estado.getOrdenesPendientes().encolar(resultado.orden());
            estado.getHistorial().apilar(resultado.accion());
            return resultado.orden();
        }
    }

    public OrdenMantenimiento cerrar(long id) {
        return cambiarEstado(id, Estado.CERRADA, Accion.Tipo.CERRAR);
    }

    public OrdenMantenimiento cancelar(long id) {
        return cambiarEstado(id, Estado.CANCELADA, Accion.Tipo.CANCELAR);
    }

    public OrdenMantenimiento atenderSiguiente() {
        synchronized (estado) {
            estado.recargar();
            OrdenMantenimiento primera = estado.getOrdenesPendientes().verPrimera();
            if (primera == null) {
                throw new RecursoNoEncontradoException("No hay órdenes pendientes en la cola de prioridad.");
            }
            return cambiarEstado(primera.getId(), Estado.CERRADA, Accion.Tipo.CERRAR);
        }
    }

    public List<AccionDTO> historial() {
        synchronized (estado) {
            estado.recargar();
            return Arrays.stream(estado.getHistorial().acciones()).map(this::aDTO).toList();
        }
    }

    public AccionDTO deshacer() {
        synchronized (estado) {
            estado.recargar();
            Accion accion = ejecutor.enTransaccion(conexion -> {
                AccionDAO acciones = new AccionDAO(conexion, objectMapper);
                acciones.bloquearPila();
                Accion tope = acciones.consultarTopeParaActualizar();
                if (tope == null) {
                    throw new RecursoNoEncontradoException("No hay acciones para deshacer.");
                }
                revertir(conexion, tope);
                if (!acciones.marcarDeshecha(tope.getIdAccion())) {
                    throw new ConflictoDatosException("La acción del tope ya fue deshecha.");
                }
                return tope;
            });
            estado.recargar();
            return aDTO(accion);
        }
    }

    private void revertir(java.sql.Connection conexion, Accion accion) throws java.sql.SQLException {
        OrdenDAO ordenes = new OrdenDAO(conexion);
        long id = accion.getIdOrden();
        switch (accion.getTipo()) {
            case CREAR -> {
                OrdenMantenimiento actual = ordenes.consultarPorIdParaActualizar(id);
                if (actual == null || !actual.getEstado().esAbierto()
                        || !ordenes.eliminarPendiente(id)) {
                    throw new ConflictoDatosException(
                            "No se pudo revertir: la orden OM-" + id + " ya no está pendiente.");
                }
            }
            case CANCELAR, CERRAR -> {
                if (!ordenes.restaurarPendiente(id)) {
                    throw new ConflictoDatosException(
                            "No se pudo revertir: la orden OM-" + id + " ya está pendiente o no existe.");
                }
            }
            case MODIFICAR -> {
                if (!ordenes.actualizar(accion.getEstadoPrevio())) {
                    throw new ConflictoDatosException("No se pudo revertir: la orden OM-" + id + " ya no existe.");
                }
            }
            case BAJA_EQUIPO -> {
                Equipo equipo = accion.getEquipoPrevio();
                EquipoDAO equipos = new EquipoDAO(conexion);
                Equipo actual = equipo == null ? null : equipos.consultarPorIdParaActualizar(equipo.getId());
                if (actual == null || (!actual.isActivo() && !equipos.restaurar(equipo))) {
                    throw new ConflictoDatosException("No se pudo reactivar el equipo eliminado.");
                }
                List<OrdenEnCola> restauradas = accion.getOrdenesEliminadas().stream()
                        .sorted(Comparator.comparingInt(OrdenEnCola::posicion)).toList();
                for (OrdenEnCola orden : restauradas) ordenes.restaurarEliminada(orden.orden());
                if (!restauradas.isEmpty()) ordenes.sincronizarSecuencias();
            }
        }
    }

    private OrdenMantenimiento cambiarEstado(long id, Estado nuevoEstado, Accion.Tipo tipoAccion) {
        synchronized (estado) {
            estado.recargar();
            CambioEstado resultado = ejecutor.enTransaccion(conexion -> {
                AccionDAO acciones = new AccionDAO(conexion, objectMapper);
                acciones.bloquearPila();
                OrdenDAO dao = new OrdenDAO(conexion);
                OrdenMantenimiento orden = dao.consultarPorIdParaActualizar(id);
                if (orden == null || !orden.getEstado().esAbierto()) {
                    throw new RecursoNoEncontradoException(
                            "La orden OM-" + id + " no existe o no está pendiente.");
                }
                OrdenMantenimiento previa = orden.copia();
                LocalDateTime cierre = LocalDateTime.now().withNano(0);
                if (!dao.cambiarEstado(id, nuevoEstado, cierre)) {
                    throw new ConflictoDatosException("La orden OM-" + id + " ya no está pendiente.");
                }
                orden.setEstado(nuevoEstado);
                orden.setFechaCierre(cierre);
                Accion accion = acciones.insertar(new Accion(tipoAccion, previa));
                return new CambioEstado(orden, accion);
            });

            estado.getOrdenesPendientes().eliminarPorId(id);
            estado.getHistorial().apilar(resultado.accion());
            return resultado.orden();
        }
    }

    private AccionDTO aDTO(Accion accion) {
        Long idOrden = accion.getEstadoPrevio() == null ? null : accion.getIdOrden();
        return new AccionDTO(accion.getTipo().name(), accion.getIdAccion(), idOrden,
                accion.getFecha(), accion.getEstadoPrevio(), accion.getEquipoPrevio(),
                accion.getOrdenesEliminadas().stream().map(OrdenEnCola::orden).toList());
    }

    private void validarOrden(String tipo, String descripcion, Integer impacto, Integer riesgoSeguridad) {
        if (!"PREVENTIVO".equals(tipo) && !"CORRECTIVO".equals(tipo)) {
            throw new DatosInvalidosException("El tipo debe ser PREVENTIVO o CORRECTIVO.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new DatosInvalidosException("La descripción es obligatoria.");
        }
        if (impacto == null || impacto < 1 || impacto > 3) {
            throw new DatosInvalidosException("El impacto de producción debe estar entre 1 y 3.");
        }
        if (riesgoSeguridad == null || riesgoSeguridad < 1 || riesgoSeguridad > 3) {
            throw new DatosInvalidosException("El riesgo de seguridad debe estar entre 1 y 3.");
        }
    }

    private RecursoNoEncontradoException noEncontrada(long id) {
        return new RecursoNoEncontradoException("No existe la orden OM-" + id + " o no está pendiente.");
    }

    private record Creacion(OrdenMantenimiento orden, Accion accion) { }
    private record Modificacion(OrdenMantenimiento orden, Accion accion) { }
    private record CambioEstado(OrdenMantenimiento orden, Accion accion) { }
}
