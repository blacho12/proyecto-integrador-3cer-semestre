package app;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import datos.AreaDAO;
import datos.ConexionBD;
import datos.EquipoDAO;
import datos.OrdenDAO;
import estructuras.Accion;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Area;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/**
 * Une las estructuras de memoria (AVL de equipos, cola de prioridad de ordenes y pila de
 * acciones) con la persistencia en PostgreSQL mediante los DAO del paquete datos.
 *
 * PostgreSQL es la fuente de verdad: ninguna operacion se considera exitosa hasta que la
 * transaccion se confirma (commit). Si falla, la memoria queda exactamente como estaba.
 * La pila de acciones solo vive en memoria: al reiniciar el programa vuelve a estar vacia.
 */
public class GestorOrdenes {
    private final ArbolAVL equipos = new ArbolAVL();
    private final ColaPrioridad ordenes = new ColaPrioridad();
    private final PilaAcciones historial = new PilaAcciones();

    private final Connection conexion;
    private final AreaDAO areaDAO;
    private final EquipoDAO equipoDAO;
    private final OrdenDAO ordenDAO;

    /** Ultimo mensaje de error comprensible; la interfaz lo muestra al usuario. */
    private volatile String ultimoError;

    /** Abre la conexion con PostgreSQL (variables DB_URL, DB_USER y DB_PASSWORD). */
    public GestorOrdenes() throws SQLException {
        this(ConexionBD.getConnection());
    }

    /** Permite inyectar una conexion propia (usado por las pruebas de integracion). */
    public GestorOrdenes(Connection conexion) throws SQLException {
        this.conexion = conexion;
        this.conexion.setAutoCommit(false);   // cada operacion publica controla commit/rollback
        this.areaDAO = new AreaDAO(conexion);
        this.equipoDAO = new EquipoDAO(conexion);
        this.ordenDAO = new OrdenDAO(conexion);
    }

    public ArbolAVL getEquipos() { return equipos; }
    public ColaPrioridad getOrdenes() { return ordenes; }
    public PilaAcciones getHistorial() { return historial; }
    public AreaDAO getAreaDAO() { return areaDAO; }
    public EquipoDAO getEquipoDAO() { return equipoDAO; }
    public OrdenDAO getOrdenDAO() { return ordenDAO; }
    public Connection getConexionBD() { return conexion; }

    /** Ultimo error de negocio o de PostgreSQL, o null si la ultima operacion fue exitosa. */
    public String getUltimoError() { return ultimoError; }

    /** Comprueba realmente si la conexion JDBC sigue abierta y responde. */
    public boolean conexionActiva() {
        try {
            return !conexion.isClosed() && conexion.isValid(2);
        } catch (SQLException e) {
            registrarError(e);
            return false;
        }
    }

    /** Cierra la conexion subyacente. */
    public void cerrar() {
        try { conexion.close(); } catch (SQLException e) { registrarError(e); }
    }

    // ------------------------------------------------------------------ inicializacion

    /**
     * Carga desde PostgreSQL lo que la sesion necesita en memoria:
     * todos los equipos al AVL y solo las ordenes PENDIENTE a la cola.
     * La pila de acciones se crea vacia (no se persiste ni se reconstruye).
     */
    public void inicializar() throws SQLException {
        for (Equipo e : equipoDAO.consultarTodos()) {
            if (equipos.buscar(e.getCodigo()) == null) equipos.insertar(e);  // sin duplicados
        }
        for (OrdenMantenimiento o : ordenDAO.consultarPorEstado(Estado.PENDIENTE)) {
            if (ordenes.buscarPorId(o.getId()) == null) ordenes.encolar(o);  // sin duplicados
        }
        // Las ordenes CERRADA y CANCELADA permanecen solo en PostgreSQL, no entran a la cola.
    }

    // ------------------------------------------------------------------ areas y equipos

    /** Registra un area en PostgreSQL y devuelve el objeto con su id generado; null si falla. */
    public Area registrarArea(String nombre, String descripcion) {
        try {
            Area area = areaDAO.insertar(nombre, descripcion);
            conexion.commit();
            return area;
        } catch (SQLException e) { notificarErrorDeBD(e); return null; }
    }

    /** Registra un equipo en PostgreSQL y lo inserta en el AVL; null si falla. */
    public Equipo registrarEquipo(Equipo equipo) {
        try {
            equipoDAO.insertar(equipo);                 // la BD valida la clave foranea del area
            equipos.insertar(equipo);                   // se mantiene sincronizado con la BD
            conexion.commit();
            return equipo;
        } catch (SQLException e) { notificarErrorDeBD(e); return null; }
    }

    /** Elimina un equipo de la BD y del AVL; la BD rechaza si tiene ordenes asociadas. */
    public boolean eliminarEquipo(String codigo) {
        Equipo e = equipos.buscar(codigo);
        if (e == null) {
            ultimoError = "El equipo '" + codigo + "' no existe en el indice AVL.";
            return false;
        }
        try {
            if (!equipoDAO.eliminar(codigo)) return false;
            equipos.eliminar(codigo);
            conexion.commit();
            return true;
        } catch (SQLException ex) { notificarErrorDeBD(ex); return false; }
    }

    public List<Area> consultarAreas() throws SQLException { return areaDAO.consultarTodas(); }

    // ------------------------------------------------------------------ crear orden

    /**
     * Crea una orden verificando el equipo en el AVL y en PostgreSQL, perssiste la orden
     * (la BD devuelve id, consecutivo y fecha de creacion), la encola y apila la accion CREAR.
     * Si el INSERT falla no se toca la cola ni la pila. Devuelve null si falla.
     */
    public OrdenMantenimiento crearOrden(String codigoEquipo, String tipo, String descripcion,
                                         int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        if (equipos.buscar(codigoEquipo) == null) {
            ultimoError = "El equipo '" + codigoEquipo + "' no existe en el catalogo de equipos.";
            System.err.println(ultimoError);
            return null;
        }
        try {
            if (equipoDAO.consultarPorCodigo(codigoEquipo) == null) {
                ultimoError = "El equipo '" + codigoEquipo + "' no existe en PostgreSQL.";
                System.err.println(ultimoError);
                return null;
            }
        } catch (SQLException e) { notificarErrorDeBD(e); return null; }
        try {
            validarDatosOrden(tipo, descripcion, impacto, riesgoSeguridad);
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            System.err.println(e.getMessage());
            return null;
        }

        OrdenMantenimiento nueva = new OrdenMantenimiento(codigoEquipo, tipo, descripcion,
                impacto, riesgoSeguridad, hayRespaldo);
        try {
            OrdenMantenimiento creada = ordenDAO.insertar(nueva);   // BD asigna id, consecutivo y fecha
            ordenes.encolar(creada);
            historial.apilar(new Accion(Accion.Tipo.CREAR, creada.copia()));
            conexion.commit();
            return creada;
        } catch (SQLException e) {
            notificarErrorDeBD(e);                      // la cola y la pila quedan intactas
            return null;
        }
    }

    // ------------------------------------------------------------------ modificar orden

    /**
     * Modifica los campos editables de una orden PENDIENTE. Retira temporalmente la orden de
     * la cola, aplica el cambio, actualiza PostgreSQL y la reencola para recalcular prioridad.
     * Si la BD falla retorna los valores anteriores en memoria y en la cola.
     */
    public boolean modificarOrden(long id, String tipo, String descripcion,
                                  int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        OrdenMantenimiento o = ordenes.buscarPorId(id);
        if (o == null || o.getEstado() != Estado.PENDIENTE) {
            ultimoError = "La orden OM-" + id + " no existe o no esta pendiente.";
            System.err.println(ultimoError);
            return false;
        }
        try {
            validarDatosOrden(tipo, descripcion, impacto, riesgoSeguridad);
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            System.err.println(e.getMessage());
            return false;
        }

        OrdenMantenimiento previa = o.copia();      // copia completa de los valores anteriores
        ordenes.eliminarPorId(id);                  // retiro temporal para recalcular prioridad
        o.setTipo(tipo);
        o.setDescripcion(descripcion);
        o.setCriterios(impacto, riesgoSeguridad, hayRespaldo);
        try {
            if (!ordenDAO.actualizar(o)) {
                restaurarValores(o, previa);
                ordenes.encolar(o);
                conexion.rollback();
                ultimoError = "La orden OM-" + id + " ya no existe en PostgreSQL.";
                System.err.println(ultimoError);
                return false;
            }
            ordenes.encolar(o);                     // reingreso con la nueva prioridad
            historial.apilar(new Accion(Accion.Tipo.MODIFICAR, previa));
            conexion.commit();
            return true;
        } catch (SQLException e) {
            restaurarValores(o, previa);            // estado anterior en memoria
            ordenes.encolar(o);                     // y de nuevo en la cola
            notificarErrorDeBD(e);
            return false;
        }
    }

    // ------------------------------------------------------------------ cancelar orden

    /** Cancela una orden pendiente: la BD a CANCELADA con fecha de cierre y sale de la cola. */
    public boolean cancelarOrden(long id) {
        OrdenMantenimiento o = ordenes.buscarPorId(id);
        if (o == null || o.getEstado() != Estado.PENDIENTE) {
            ultimoError = "La orden OM-" + id + " no existe o no esta pendiente.";
            System.err.println(ultimoError);
            return false;
        }
        OrdenMantenimiento previa = o.copia();
        LocalDateTime cierre = LocalDateTime.now();
        try {
            if (!ordenDAO.cambiarEstado(id, Estado.CANCELADA, cierre)) {
                ultimoError = "La orden OM-" + id + " ya no esta pendiente en PostgreSQL.";
                return false;
            }
            ordenes.eliminarPorId(id);              // despues de que la BD confirmo el cambio
            o.setEstado(Estado.CANCELADA);
            o.setFechaCierre(cierre);
            historial.apilar(new Accion(Accion.Tipo.CANCELAR, previa));
            conexion.commit();
            return true;
        } catch (SQLException e) {
            notificarErrorDeBD(e);                  // la orden permanece en la cola pendiente
            return false;
        }
    }

    // ------------------------------------------------------------------ cerrar orden

    /**
     * Cierra una orden PENDIENTE concreta. Primero confirma el cambio en PostgreSQL y solo
     * despues la retira de la cola y apila la accion CERRAR para poder deshacerla.
     * Devuelve false si no existe, no esta pendiente o falla la base de datos.
     */
    public boolean cerrarOrden(long id) {
        OrdenMantenimiento o = ordenes.buscarPorId(id);
        if (o == null || o.getEstado() != Estado.PENDIENTE) {
            ultimoError = "La orden OM-" + id + " no existe o no esta pendiente.";
            System.err.println(ultimoError);
            return false;
        }
        OrdenMantenimiento previa = o.copia();
        LocalDateTime cierre = LocalDateTime.now();
        try {
            if (!ordenDAO.cambiarEstado(id, Estado.CERRADA, cierre)) {
                ultimoError = "La orden OM-" + id + " ya no esta pendiente en PostgreSQL.";
                return false;
            }
            ordenes.eliminarPorId(id);
            o.setEstado(Estado.CERRADA);
            o.setFechaCierre(cierre);
            historial.apilar(new Accion(Accion.Tipo.CERRAR, previa));
            conexion.commit();
            return true;
        } catch (SQLException e) {
            notificarErrorDeBD(e);
            return false;
        }
    }

    /** Atiende (cierra) la orden mas prioritaria. Si la BD falla, permanece en la cola. */
    public OrdenMantenimiento atenderSiguiente() {
        OrdenMantenimiento o = ordenes.verPrimera();
        if (o == null) return null;
        OrdenMantenimiento previa = o.copia();
        LocalDateTime cierre = LocalDateTime.now();
        try {
            if (!ordenDAO.cambiarEstado(o.getId(), Estado.CERRADA, cierre)) {
                ultimoError = "La orden OM-" + o.getId() + " ya no esta pendiente en PostgreSQL.";
                System.err.println(ultimoError);
                return null;
            }
            ordenes.desencolar();
            o.setEstado(Estado.CERRADA);
            o.setFechaCierre(cierre);
            historial.apilar(new Accion(Accion.Tipo.CERRAR, previa));
            conexion.commit();
            return o;
        } catch (SQLException e) {
            notificarErrorDeBD(e);                  // la orden permanece en la cola pendiente
            return null;
        }
    }

    // ------------------------------------------------------------------ consultar en la BD

    /** Historial completo de ordenes (PENDIENTE, CERRADA y CANCELADA) para las vistas. */
    public List<OrdenMantenimiento> consultarTodasLasOrdenes() throws SQLException {
        return ordenDAO.consultarTodas();
    }

    public List<OrdenMantenimiento> consultarOrdenesPorEquipo(String codigoEquipo) throws SQLException {
        return ordenDAO.consultarPorEquipo(codigoEquipo);
    }

    public List<OrdenMantenimiento> consultarOrdenesPorEstado(Estado estado) throws SQLException {
        return ordenDAO.consultarPorEstado(estado);
    }

    public OrdenMantenimiento consultarOrden(long id) throws SQLException {
        return ordenDAO.consultarPorId(id);
    }

    // ------------------------------------------------------------------ deshacer

    /**
     * Deshace la ultima accion (LIFO). La revision se hace primero en PostgreSQL dentro de
     * la transaccion: solo si el commit es exitoso se retira la accion de la pila.
     * Si la revision falla, la accion permanece en la pila y la memoria queda sin cambios.
     * Devuelve la accion revertida, o null si no hay historial o fallo la revision.
     */
    public Accion deshacer() {
        Accion accion = historial.verTope();
        if (accion == null) return null;
        OrdenMantenimiento previa = accion.getEstadoPrevio();
        long id = accion.getIdOrden();
        try {
            switch (accion.getTipo()) {
                case CREAR:
                    // Revertir una creacion = eliminar el registro y retirarlo de las estructuras.
                    if (!ordenDAO.eliminar(id)) {
                        ultimoError = "No se pudo revertir: la orden OM-" + id + " ya no existe en PostgreSQL.";
                        System.err.println(ultimoError);
                        return null;
                    }
                    ordenes.eliminarPorId(id);
                    break;

                case CANCELAR:
                case CERRAR:
                    // Restaurar PENDIENTE con fecha_cierre NULL en la BD y reinsertar en la cola.
                    if (!ordenDAO.restaurarPendiente(id)) {
                        ultimoError = "No se pudo revertir: la orden OM-" + id + " ya esta pendiente o no existe.";
                        System.err.println(ultimoError);
                        return null;
                    }
                    ordenes.encolar(previa);        // snapshot: pendiente, sin fecha de cierre
                    break;

                case MODIFICAR:
                    // Restaurar los valores del snapshot en la BD y recalcular prioridad.
                    if (!ordenDAO.actualizar(previa)) {
                        ultimoError = "No se pudo revertir: la orden OM-" + id + " ya no existe.";
                        System.err.println(ultimoError);
                        return null;
                    }
                    OrdenMantenimiento actual = ordenes.buscarPorId(id);
                    if (actual != null) {
                        ordenes.eliminarPorId(id);  // retirar, restaurar valores y recalcular
                        restaurarValores(actual, previa);
                        ordenes.encolar(actual);
                    } else {
                        ordenes.encolar(previa.copia());
                    }
                    break;
            }
            historial.desapilar();
            conexion.commit();
            return accion;
        } catch (SQLException e) {
            notificarErrorDeBD(e);                  // la accion permanece en la pila
            return null;
        }
    }

    // ------------------------------------------------------------------ utilidades privadas

    /** Devuelve los valores del snapshot a un objeto vivo (solo campos editables). */
    private void restaurarValores(OrdenMantenimiento destino, OrdenMantenimiento previa) {
        destino.setTipo(previa.getTipo());
        destino.setDescripcion(previa.getDescripcion());
        destino.setCriterios(previa.getImpacto(), previa.getRiesgoSeguridad(), previa.isHayRespaldo());
    }

    /** Valida los datos de una orden con las mismas reglas CHECK de la tabla. */
    private void validarDatosOrden(String tipo, String descripcion,
                                   int impacto, int riesgoSeguridad) {
        if (!"PREVENTIVO".equals(tipo) && !"CORRECTIVO".equals(tipo)) {
            throw new IllegalArgumentException("El tipo debe ser PREVENTIVO o CORRECTIVO.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion es obligatoria.");
        }
        if (impacto < 1 || impacto > 3) {
            throw new IllegalArgumentException("El impacto de produccion debe estar entre 1 y 3.");
        }
        if (riesgoSeguridad < 1 || riesgoSeguridad > 3) {
            throw new IllegalArgumentException("El riesgo de seguridad debe estar entre 1 y 3.");
        }
    }

    /** Hace rollback e imprime un error comprensible. La memoria queda sin cambios. */
    private void notificarErrorDeBD(SQLException e) {
        try { conexion.rollback(); } catch (SQLException r) { registrarError(r); }
        ultimoError = "Error en PostgreSQL: " + ConexionBD.mensajeAmigable(e);
        System.err.println(ultimoError);
    }

    /** Cancela la transaccion actual; automaticamente la BD descarta todo cambio pendiente. */
    private void revertir() {
        try { conexion.rollback(); } catch (SQLException e) { registrarError(e); }
    }

    private void registrarError(SQLException e) {
        System.err.println("Error en PostgreSQL: " + ConexionBD.mensajeAmigable(e));
    }
}
