package app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import datos.AreaDAO;
import datos.CarbonoDAO;
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
import modelo.RegistroCarbono;
import modelo.ResumenCarbono;
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

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final Connection conexion;
    private final AreaDAO areaDAO;
    private final EquipoDAO equipoDAO;
    private final OrdenDAO ordenDAO;
    private final CarbonoDAO carbonoDAO;

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
        this.carbonoDAO = new CarbonoDAO(conexion);
    }

    public ArbolAVL getEquipos() { return equipos; }
    public ColaPrioridad getOrdenes() { return ordenes; }
    public PilaAcciones getHistorial() { return historial; }
    public AreaDAO getAreaDAO() { return areaDAO; }
    public EquipoDAO getEquipoDAO() { return equipoDAO; }
    public OrdenDAO getOrdenDAO() { return ordenDAO; }
    public CarbonoDAO getCarbonoDAO() { return carbonoDAO; }
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

    /**
     * Registra un area validando nombre obligatorio, longitud y duplicados (sin distinguir
     * mayusculas). Devuelve el objeto con su id generado o null si la operacion falla.
     */
    public Area registrarArea(String nombre, String descripcion) {
        try {
            String nombreLimpio = validarNombreArea(nombre);
            String descripcionLimpia = normalizarDescripcionArea(descripcion);
            if (areaDAO.existeNombre(nombreLimpio, 0)) {
                ultimoError = "Ya existe un area con el nombre «" + nombreLimpio + "».";
                return null;
            }
            Area area = areaDAO.insertar(nombreLimpio, descripcionLimpia);
            conexion.commit();
            return area;
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            return null;
        } catch (SQLException e) { notificarErrorDeBD(e); return null; }
    }

    /** Modifica nombre y descripcion de un area existente; false si falla o hay duplicado. */
    public boolean modificarArea(int idArea, String nombre, String descripcion) {
        try {
            String nombreLimpio = validarNombreArea(nombre);
            String descripcionLimpia = normalizarDescripcionArea(descripcion);
            if (areaDAO.consultarPorId(idArea) == null) {
                ultimoError = "El area con id " + idArea + " no existe en PostgreSQL.";
                return false;
            }
            if (areaDAO.existeNombre(nombreLimpio, idArea)) {
                ultimoError = "Ya existe otra area con el nombre «" + nombreLimpio + "».";
                return false;
            }
            if (!areaDAO.actualizar(new Area(idArea, nombreLimpio, descripcionLimpia))) {
                conexion.rollback();
                ultimoError = "El area con id " + idArea + " ya no existe en PostgreSQL.";
                return false;
            }
            conexion.commit();
            return true;
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            return false;
        } catch (SQLException e) { notificarErrorDeBD(e); return false; }
    }

    /**
     * Elimina un area solo si no tiene equipos asociados. No se hace borrado en cascada:
     * primero se comprueba en Java para explicar el motivo y la clave foranea fk_equipo_area
     * (ON DELETE RESTRICT) actua como segunda barrera.
     */
    public boolean eliminarArea(int idArea) {
        try {
            Area area = areaDAO.consultarPorId(idArea);
            if (area == null) {
                ultimoError = "El area con id " + idArea + " no existe en PostgreSQL.";
                return false;
            }
            int equiposAsociados = areaDAO.contarEquipos(idArea);
            if (equiposAsociados > 0) {
                ultimoError = "No se puede eliminar el area «" + area.getNombre() + "»: tiene "
                        + equiposAsociados + " equipo(s) asociado(s). Reasigne o elimine primero "
                        + "esos equipos; no se permite el borrado en cascada.";
                return false;
            }
            if (!areaDAO.eliminar(idArea)) {
                conexion.rollback();
                ultimoError = "El area con id " + idArea + " ya no existe en PostgreSQL.";
                return false;
            }
            conexion.commit();
            return true;
        } catch (SQLException e) { notificarErrorDeBD(e); return false; }
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

    /**
     * Corrige nombre, area y, si el equipo no tiene historial, el codigo. El codigo es la clave
     * del AVL y de las claves foraneas; se bloquea su cambio cuando existen ordenes o registros
     * de carbono para no romper la trazabilidad. Mantiene el AVL sincronizado con PostgreSQL.
     */
    public boolean modificarEquipo(String codigoActual, String nuevoCodigo, String nombre, int idArea) {
        try {
            String actual = codigoActual == null ? "" : codigoActual.trim();
            String nuevo = nuevoCodigo == null ? "" : nuevoCodigo.trim().toUpperCase();
            String nombreLimpio = validarNombreEquipo(nombre);

            if (actual.isEmpty()) {
                throw new IllegalArgumentException("Seleccione un equipo para modificarlo.");
            }
            validarCodigoEquipo(nuevo);

            Equipo existente = equipos.buscar(actual);
            if (existente == null) {
                ultimoError = "El equipo '" + actual + "' no existe en el indice AVL.";
                return false;
            }
            if (areaDAO.consultarPorId(idArea) == null) {
                ultimoError = "El area con id " + idArea + " no existe en PostgreSQL.";
                return false;
            }

            boolean cambiaCodigo = !nuevo.equals(actual);
            if (cambiaCodigo) {
                if (equipoDAO.consultarPorCodigo(nuevo) != null) {
                    ultimoError = "Ya existe un equipo con el codigo '" + nuevo + "'.";
                    return false;
                }
                if (equipoDAO.tieneDependencias(actual)) {
                    ultimoError = "No se puede cambiar el codigo del equipo '" + actual
                            + "': tiene ordenes o registros de carbono asociados. "
                            + "Puede corregir el nombre o el area sin alterar el historial.";
                    return false;
                }
            }

            if (!equipoDAO.actualizar(actual, nuevo, nombreLimpio, idArea)) {
                conexion.rollback();
                ultimoError = "El equipo '" + actual + "' ya no existe en PostgreSQL.";
                return false;
            }

            if (!cambiaCodigo) {
                existente.setNombre(nombreLimpio);
                existente.setIdArea(idArea);
            } else {
                Equipo actualizado = new Equipo(nuevo, nombreLimpio, idArea,
                        existente.getConsumoNormalKwh(), existente.getPorcentajeExtra());
                boolean retirado = equipos.eliminar(actual);
                boolean insertado = equipos.insertar(actualizado);
                if (!retirado || !insertado) {
                    equipos.eliminar(nuevo);
                    equipos.insertar(existente);
                    conexion.rollback();
                    ultimoError = "No se pudo actualizar el indice AVL; el cambio se revirtio.";
                    return false;
                }
            }
            conexion.commit();
            return true;
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            return false;
        } catch (SQLException e) { notificarErrorDeBD(e); return false; }
    }

    /**
     * Elimina un equipo de PostgreSQL y del AVL. No hay borrado en cascada: se rechaza si tiene
     * ordenes o registros de carbono para conservar el historial de mantenimiento y emisiones.
     */
    public boolean eliminarEquipo(String codigo) {
        String codigoLimpio = codigo == null ? "" : codigo.trim();
        Equipo e = equipos.buscar(codigoLimpio);
        if (e == null) {
            ultimoError = "El equipo '" + codigoLimpio + "' no existe en el indice AVL.";
            return false;
        }
        try {
            if (equipoDAO.tieneDependencias(codigoLimpio)) {
                ultimoError = "No se puede eliminar el equipo '" + codigoLimpio
                        + "': tiene ordenes o registros de carbono asociados. "
                        + "Se conserva el historial; no se hace borrado en cascada.";
                return false;
            }
            if (!equipoDAO.eliminar(codigoLimpio)) {
                conexion.rollback();
                ultimoError = "El equipo '" + codigoLimpio + "' ya no existe en PostgreSQL.";
                return false;
            }
            if (!equipos.eliminar(codigoLimpio)) {
                conexion.rollback();
                ultimoError = "No se pudo retirar el equipo '" + codigoLimpio + "' del indice AVL.";
                return false;
            }
            conexion.commit();
            return true;
        } catch (SQLException ex) { notificarErrorDeBD(ex); return false; }
    }

    public List<Area> consultarAreas() throws SQLException { return areaDAO.consultarTodas(); }

    /** Cantidad de equipos por area para la tabla de gestion de areas. */
    public Map<Integer, Integer> contarEquiposPorArea() throws SQLException {
        return areaDAO.contarEquiposPorArea();
    }

    /** Equipos persistidos (incluye los parametros de consumo de carbono). */
    public List<Equipo> consultarEquipos() throws SQLException { return equipoDAO.consultarTodos(); }

    // ------------------------------------------------------------------ huella de carbono

    /**
     * Guarda el consumo normal (kWh/h) y el porcentaje extra de un equipo. Ambos datos viven
     * en la fila del equipo: no se repiten en cada registro de carbono. Devuelve false si el
     * equipo no existe o si algun valor es negativo.
     */
    public boolean configurarParametrosEquipo(String codigo, BigDecimal consumoNormalKwh,
                                              BigDecimal porcentajeExtra) {
        try {
            if (codigo == null || codigo.isBlank()) {
                throw new IllegalArgumentException("El codigo del equipo es obligatorio.");
            }
            CalculadoraCarbono.validarNoNegativo("consumo energetico normal", consumoNormalKwh);
            CalculadoraCarbono.validarNoNegativo("porcentaje de consumo adicional", porcentajeExtra);
            if (porcentajeExtra.compareTo(CIEN) > 0) {
                throw new IllegalArgumentException(
                        "El porcentaje de consumo adicional no puede superar 100.");
            }

            BigDecimal consumoLimpio = consumoNormalKwh.setScale(2, RoundingMode.HALF_UP);
            BigDecimal porcentajeLimpio = porcentajeExtra.setScale(2, RoundingMode.HALF_UP);

            if (equipoDAO.consultarPorCodigo(codigo) == null) {
                ultimoError = "El equipo '" + codigo + "' no existe en PostgreSQL.";
                return false;
            }
            if (!equipoDAO.actualizarParametrosCarbono(codigo, consumoLimpio, porcentajeLimpio)) {
                conexion.rollback();
                ultimoError = "El equipo '" + codigo + "' ya no existe en PostgreSQL.";
                return false;
            }
            conexion.commit();

            Equipo enMemoria = equipos.buscar(codigo);
            if (enMemoria != null) {
                enMemoria.setConsumoNormalKwh(consumoLimpio);
                enMemoria.setPorcentajeExtra(porcentajeLimpio);
            }
            return true;
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            return false;
        } catch (SQLException e) { notificarErrorDeBD(e); return false; }
    }

    /**
     * Calcula y persiste las emisiones adicionales de un equipo para las horas indicadas.
     * Usa el consumo y el porcentaje configurados en el equipo y el factor vigente de la
     * planta, que queda guardado en el registro para conservar la trazabilidad.
     * Devuelve null si el equipo no existe, hay datos invalidos o falla PostgreSQL.
     */
    public RegistroCarbono registrarEmisionCarbono(String codigoEquipo, BigDecimal horasMalEstado) {
        try {
            if (codigoEquipo == null || codigoEquipo.isBlank()) {
                throw new IllegalArgumentException("Seleccione un equipo registrado.");
            }
            CalculadoraCarbono.validarNoNegativo("horas en mal estado", horasMalEstado);
            BigDecimal horas = horasMalEstado.setScale(2, RoundingMode.HALF_UP);

            Equipo equipo = equipoDAO.consultarPorCodigo(codigoEquipo);
            if (equipo == null) {
                ultimoError = "El equipo '" + codigoEquipo + "' no existe en PostgreSQL.";
                return null;
            }

            BigDecimal factor = carbonoDAO.consultarFactorEmision();
            CalculadoraCarbono.Resultado calculo = CalculadoraCarbono.calcular(
                    equipo.getConsumoNormalKwh(), horas, equipo.getPorcentajeExtra(), factor);

            Area area = areaDAO.consultarPorId(equipo.getIdArea());
            String nombreArea = area == null ? "Area " + equipo.getIdArea() : area.getNombre();

            RegistroCarbono registro = carbonoDAO.insertar(codigoEquipo, equipo.getNombre(),
                    nombreArea, horas, calculo.getConsumoAdicionalKwh(),
                    calculo.getEmisionesKgCo2(), factor);
            conexion.commit();
            return registro;
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            return null;
        } catch (SQLException e) { notificarErrorDeBD(e); return null; }
    }

    public BigDecimal consultarFactorEmision() throws SQLException {
        return carbonoDAO.consultarFactorEmision();
    }

    /** Actualiza el factor de emision de la planta (kg CO2 por kWh). */
    public boolean actualizarFactorEmision(BigDecimal factorEmision) {
        try {
            CalculadoraCarbono.validarNoNegativo("factor de emision", factorEmision);
            BigDecimal factorLimpio = factorEmision.setScale(4, RoundingMode.HALF_UP);
            carbonoDAO.actualizarFactorEmision(factorLimpio);
            conexion.commit();
            return true;
        } catch (IllegalArgumentException e) {
            ultimoError = e.getMessage();
            return false;
        } catch (SQLException e) { notificarErrorDeBD(e); return false; }
    }

    /**
     * Consolida los registros almacenados en PostgreSQL: total de la planta, emisiones por
     * area, emisiones por equipo y numero de equipos con emisiones mayores que cero.
     * No usa valores simulados: todo proviene de registro_carbono.
     */
    public ResumenCarbono calcularResumenCarbono() throws SQLException {
        List<RegistroCarbono> registros = carbonoDAO.consultarTodos();
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> porArea = new LinkedHashMap<>();
        Map<String, BigDecimal> porEquipo = new LinkedHashMap<>();
        Set<String> equiposConEmisiones = new LinkedHashSet<>();

        for (RegistroCarbono registro : registros) {
            BigDecimal emisiones = registro.getEmisionesKgCo2();
            total = total.add(emisiones);

            String area = registro.getNombreArea() == null ? "Sin area" : registro.getNombreArea();
            porArea.merge(area, emisiones, BigDecimal::add);
            porEquipo.merge(registro.getCodigoEquipo(), emisiones, BigDecimal::add);
            if (emisiones.signum() > 0) equiposConEmisiones.add(registro.getCodigoEquipo());
        }

        return new ResumenCarbono(registros, total.setScale(4, RoundingMode.HALF_UP),
                porArea, porEquipo, equiposConEmisiones.size());
    }

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

    /** Valida el codigo de un equipo (VARCHAR(30), obligatorio). */
    private void validarCodigoEquipo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El codigo del equipo es obligatorio.");
        }
        if (codigo.length() > 30) {
            throw new IllegalArgumentException(
                    "El codigo del equipo no puede exceder 30 caracteres.");
        }
    }

    /** Valida y normaliza el nombre de un equipo (VARCHAR(120), obligatorio). */
    private String validarNombreEquipo(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del equipo es obligatorio.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 120) {
            throw new IllegalArgumentException(
                    "El nombre del equipo no puede exceder 120 caracteres.");
        }
        return limpio;
    }

    /** Valida y normaliza el nombre de un area (VARCHAR(100), obligatorio). */
    private String validarNombreArea(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del area es obligatorio.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 100) {
            throw new IllegalArgumentException(
                    "El nombre del area no puede exceder 100 caracteres.");
        }
        return limpio;
    }

    /** Valida y normaliza la descripcion de un area (VARCHAR(250), opcional). */
    private String normalizarDescripcionArea(String descripcion) {
        if (descripcion == null) return null;
        String limpia = descripcion.trim();
        if (limpia.length() > 250) {
            throw new IllegalArgumentException(
                    "La descripcion del area no puede exceder 250 caracteres.");
        }
        return limpia.isEmpty() ? null : limpia;
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
