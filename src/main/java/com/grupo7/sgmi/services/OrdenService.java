package com.grupo7.sgmi.services;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.grupo7.sgmi.dto.AccionDTO;
import com.grupo7.sgmi.dto.OrdenActualizarRequest;
import com.grupo7.sgmi.dto.OrdenRequest;
import com.grupo7.sgmi.excepciones.ConflictoDatosException;
import com.grupo7.sgmi.excepciones.DatosInvalidosException;
import com.grupo7.sgmi.excepciones.RecursoNoEncontradoException;

import datos.EquipoDAO;
import datos.OrdenDAO;
import estructuras.Accion;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/**
 * Reglas de negocio de las ordenes de mantenimiento.
 *
 * Cada operacion se confirma primero en PostgreSQL y solo despues se actualizan
 * la cola de prioridad y la pila de acciones. Si la base falla, las estructuras
 * quedan como estaban. La cola conserva el orden de atencion calculado por las
 * estructuras academicas: mayor criticidad primero y, a igual criticidad, la mas antigua.
 */
@Service
public class OrdenService {

    private final EjecutorBD ejecutor;
    private final EstadoEnMemoria estado;

    public OrdenService(EjecutorBD ejecutor, EstadoEnMemoria estado) {
        this.ejecutor = ejecutor;
        this.estado = estado;
    }

    /** Historico completo o filtrado por estado y/o equipo. */
    public List<OrdenMantenimiento> listar(Estado estadoOrden, String codigoEquipo) {
        String codigo = (codigoEquipo == null || codigoEquipo.isBlank()) ? null : codigoEquipo.trim();
        return ejecutor.enLectura(conexion -> {
            OrdenDAO dao = new OrdenDAO(conexion);
            if (estadoOrden != null && codigo != null) {
                return dao.consultarPorEstado(estadoOrden).stream()
                        .filter(orden -> codigo.equals(orden.getCodigoEquipo()))
                        .toList();
            }
            if (estadoOrden != null) {
                return dao.consultarPorEstado(estadoOrden);
            }
            if (codigo != null) {
                return dao.consultarPorEquipo(codigo);
            }
            return dao.consultarTodas();
        });
    }

    public OrdenMantenimiento obtener(long id) {
        OrdenMantenimiento orden = ejecutor.enLectura(conexion -> new OrdenDAO(conexion).consultarPorId(id));
        if (orden == null) {
            throw noEncontrada(id);
        }
        return orden;
    }

    /** Ordenes PENDIENTE en orden de atencion, sin modificarlo. La primera es la mas prioritaria. */
    public List<OrdenMantenimiento> cola() {
        synchronized (estado) {
            estado.asegurarCargado();
            return List.of(estado.getOrdenesPendientes().recorridoPorPrioridad());
        }
    }

    /** Registra una orden; PostgreSQL asigna id, consecutivo y fecha de creacion. */
    public OrdenMantenimiento crear(OrdenRequest peticion) {
        String codigo = peticion.codigoEquipo().trim();
        String tipo = peticion.tipo().trim().toUpperCase(Locale.ROOT);
        String descripcion = peticion.descripcion().trim();
        validarOrden(tipo, descripcion, peticion.impacto(), peticion.riesgoSeguridad());

        synchronized (estado) {
            estado.asegurarCargado();
            if (estado.getEquipos().buscar(codigo) == null) {
                throw new RecursoNoEncontradoException(
                        "El equipo '" + codigo + "' no existe en el catalogo de equipos.");
            }
            OrdenMantenimiento creada = ejecutor.enTransaccion(conexion -> {
                if (new EquipoDAO(conexion).consultarPorCodigo(codigo) == null) {
                    throw new RecursoNoEncontradoException(
                            "El equipo '" + codigo + "' no existe en PostgreSQL.");
                }
                OrdenMantenimiento nueva = new OrdenMantenimiento(
                        codigo, tipo, descripcion, peticion.impacto(),
                        peticion.riesgoSeguridad(), peticion.hayRespaldo());
                return new OrdenDAO(conexion).insertar(nueva);
            });
            estado.getOrdenesPendientes().encolar(creada);
            estado.getHistorial().apilar(new Accion(Accion.Tipo.CREAR, creada.copia()));
            return creada;
        }
    }

    /** Modifica los campos editables de una orden PENDIENTE y recalcula su prioridad. */
    public OrdenMantenimiento modificar(long id, OrdenActualizarRequest peticion) {
        String tipo = peticion.tipo().trim().toUpperCase(Locale.ROOT);
        String descripcion = peticion.descripcion().trim();
        validarOrden(tipo, descripcion, peticion.impacto(), peticion.riesgoSeguridad());

        synchronized (estado) {
            estado.asegurarCargado();
            ColaPrioridad cola = estado.getOrdenesPendientes();
            OrdenMantenimiento orden = cola.buscarPorId(id);
            if (orden == null || orden.getEstado() != Estado.PENDIENTE) {
                throw noEncontrada(id);
            }

            OrdenMantenimiento previa = orden.copia();
            cola.eliminarPorId(id);
            orden.setTipo(tipo);
            orden.setDescripcion(descripcion);
            orden.setCriterios(peticion.impacto(), peticion.riesgoSeguridad(), peticion.hayRespaldo());

            boolean actualizada;
            try {
                actualizada = ejecutor.enTransaccion(conexion -> new OrdenDAO(conexion).actualizar(orden));
            } catch (RuntimeException e) {
                restaurarValores(orden, previa);
                cola.encolar(orden);
                throw e;
            }
            if (!actualizada) {
                restaurarValores(orden, previa);
                cola.encolar(orden);
                throw new RecursoNoEncontradoException("La orden OM-" + id + " ya no existe en PostgreSQL.");
            }

            cola.encolar(orden);
            estado.getHistorial().apilar(new Accion(Accion.Tipo.MODIFICAR, previa));
            return orden;
        }
    }

    public OrdenMantenimiento cerrar(long id) {
        return cambiarEstado(id, Estado.CERRADA, Accion.Tipo.CERRAR);
    }

    public OrdenMantenimiento cancelar(long id) {
        return cambiarEstado(id, Estado.CANCELADA, Accion.Tipo.CANCELAR);
    }

    /** Atiende (cierra) la orden de mayor prioridad de la cola. */
    public OrdenMantenimiento atenderSiguiente() {
        synchronized (estado) {
            estado.asegurarCargado();
            OrdenMantenimiento primera = estado.getOrdenesPendientes().verPrimera();
            if (primera == null) {
                throw new RecursoNoEncontradoException(
                        "No hay ordenes pendientes en la cola de prioridad.");
            }
            return cerrar(primera.getId());
        }
    }

    /** Acciones apiladas, de la mas reciente a la mas antigua. */
    public List<AccionDTO> historial() {
        synchronized (estado) {
            estado.asegurarCargado();
            return Arrays.stream(estado.getHistorial().acciones())
                    .map(this::aDTO)
                    .toList();
        }
    }

    /**
     * Deshace la ultima accion (LIFO). La revision se confirma en PostgreSQL antes
     * de retirar la accion de la pila; si falla, la pila queda intacta.
     */
    public AccionDTO deshacer() {
        synchronized (estado) {
            estado.asegurarCargado();
            PilaAcciones historial = estado.getHistorial();
            Accion accion = historial.verTope();
            if (accion == null) {
                throw new RecursoNoEncontradoException("No hay acciones para deshacer.");
            }

            OrdenMantenimiento previa = accion.getEstadoPrevio();
            long id = accion.getIdOrden();

            switch (accion.getTipo()) {
                case CREAR -> {
                    boolean eliminada = ejecutor.enTransaccion(
                            conexion -> new OrdenDAO(conexion).eliminar(id));
                    if (!eliminada) {
                        throw new ConflictoDatosException(
                                "No se pudo revertir: la orden OM-" + id + " ya no existe en PostgreSQL.");
                    }
                    estado.getOrdenesPendientes().eliminarPorId(id);
                }
                case CANCELAR, CERRAR -> {
                    boolean restaurada = ejecutor.enTransaccion(
                            conexion -> new OrdenDAO(conexion).restaurarPendiente(id));
                    if (!restaurada) {
                        throw new ConflictoDatosException(
                                "No se pudo revertir: la orden OM-" + id
                                + " ya esta pendiente o no existe.");
                    }
                    if (estado.getOrdenesPendientes().buscarPorId(id) == null) {
                        estado.getOrdenesPendientes().encolar(previa);
                    }
                }
                case MODIFICAR -> {
                    boolean actualizada = ejecutor.enTransaccion(
                            conexion -> new OrdenDAO(conexion).actualizar(previa));
                    if (!actualizada) {
                        throw new ConflictoDatosException(
                                "No se pudo revertir: la orden OM-" + id + " ya no existe.");
                    }
                    OrdenMantenimiento actual = estado.getOrdenesPendientes().buscarPorId(id);
                    if (actual != null) {
                        estado.getOrdenesPendientes().eliminarPorId(id);
                        restaurarValores(actual, previa);
                        estado.getOrdenesPendientes().encolar(actual);
                    } else {
                        estado.getOrdenesPendientes().encolar(previa.copia());
                    }
                }
            }

            historial.desapilar();
            return aDTO(accion);
        }
    }

    private OrdenMantenimiento cambiarEstado(long id, Estado nuevoEstado, Accion.Tipo tipoAccion) {
        synchronized (estado) {
            estado.asegurarCargado();
            ColaPrioridad cola = estado.getOrdenesPendientes();
            OrdenMantenimiento orden = cola.buscarPorId(id);
            if (orden == null || orden.getEstado() != Estado.PENDIENTE) {
                throw new RecursoNoEncontradoException(
                        "La orden OM-" + id + " no existe o no esta pendiente.");
            }

            OrdenMantenimiento previa = orden.copia();
            LocalDateTime cierre = LocalDateTime.now().withNano(0);
            boolean cambiada = ejecutor.enTransaccion(
                    conexion -> new OrdenDAO(conexion).cambiarEstado(id, nuevoEstado, cierre));
            if (!cambiada) {
                throw new ConflictoDatosException(
                        "La orden OM-" + id + " ya no esta pendiente en PostgreSQL.");
            }

            cola.eliminarPorId(id);
            orden.setEstado(nuevoEstado);
            orden.setFechaCierre(cierre);
            estado.getHistorial().apilar(new Accion(tipoAccion, previa));
            return orden;
        }
    }

    private AccionDTO aDTO(Accion accion) {
        return new AccionDTO(accion.getTipo().name(), accion.getIdOrden(),
                accion.getFecha(), accion.getEstadoPrevio());
    }

    private void restaurarValores(OrdenMantenimiento destino, OrdenMantenimiento previa) {
        destino.setTipo(previa.getTipo());
        destino.setDescripcion(previa.getDescripcion());
        destino.setCriterios(previa.getImpacto(), previa.getRiesgoSeguridad(), previa.isHayRespaldo());
    }

    /** Aplica las mismas reglas CHECK de la tabla antes de llegar a PostgreSQL. */
    private void validarOrden(String tipo, String descripcion, Integer impacto, Integer riesgoSeguridad) {
        if (!"PREVENTIVO".equals(tipo) && !"CORRECTIVO".equals(tipo)) {
            throw new DatosInvalidosException("El tipo debe ser PREVENTIVO o CORRECTIVO.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new DatosInvalidosException("La descripcion es obligatoria.");
        }
        if (impacto == null || impacto < 1 || impacto > 3) {
            throw new DatosInvalidosException("El impacto de produccion debe estar entre 1 y 3.");
        }
        if (riesgoSeguridad == null || riesgoSeguridad < 1 || riesgoSeguridad > 3) {
            throw new DatosInvalidosException("El riesgo de seguridad debe estar entre 1 y 3.");
        }
    }

    private RecursoNoEncontradoException noEncontrada(long id) {
        return new RecursoNoEncontradoException(
                "No existe la orden OM-" + id + " o no esta pendiente.");
    }
}
