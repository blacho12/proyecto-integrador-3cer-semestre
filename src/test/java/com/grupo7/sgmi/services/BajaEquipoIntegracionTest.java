package com.grupo7.sgmi.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grupo7.sgmi.dto.AccionDTO;
import com.grupo7.sgmi.dto.EquipoRequest;
import com.grupo7.sgmi.dto.OrdenRequest;
import com.grupo7.sgmi.excepciones.ErrorBaseDatosException;
import com.grupo7.sgmi.excepciones.RecursoNoEncontradoException;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Pruebas de integracion contra PostgreSQL real para la baja logica de equipos,
 * el snapshot persistente y el deshacer.
 *
 * Se ejecutan solo cuando TEST_DB_URL esta definido y la base destino se llama
 * test/dev/local (el test recrea el esquema publico). El script
 * scripts/probar-integracion.sh levanta una base temporal y define estas variables.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class BajaEquipoIntegracionTest {

    private static final String URL = System.getenv("TEST_DB_URL");
    private static final String USUARIO = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
    private static final String CLAVE = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static EjecutorBD ejecutor;
    private static ObjectMapper objectMapper;

    private EstadoEnMemoria estado;
    private EquipoService equipos;
    private OrdenService ordenes;
    private int idArea;

    @BeforeAll
    static void prepararBase() throws Exception {
        DriverManagerDataSource fuente = new DriverManagerDataSource(URL, USUARIO, CLAVE);
        fuente.setDriverClassName("org.postgresql.Driver");
        dataSource = fuente;
        jdbc = new JdbcTemplate(dataSource);

        String base = jdbc.queryForObject("SELECT current_database()", String.class);
        assertNotNull(base);
        if (base.matches("(?i).*prod.*") || !base.matches("(?i).*(test|dev|local).*")) {
            throw new IllegalStateException(
                    "Prueba abortada: la base '" + base + "' no parece de desarrollo/test.");
        }

        try (Connection conexion = dataSource.getConnection()) {
            try (var sentencia = conexion.createStatement()) {
                sentencia.execute("DROP SCHEMA public CASCADE");
                sentencia.execute("CREATE SCHEMA public");
            }
            ejecutarScript(conexion, "esquema.sql");
            ejecutarScript(conexion, "esquema_carbono.sql");
        }

        ejecutor = new EjecutorBD(dataSource);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterAll
    static void cerrar() {
        if (dataSource instanceof AutoCloseable cerrable) {
            assertDoesNotThrow(() -> cerrable.close());
        }
    }

    @BeforeEach
    void reiniciarDatos() {
        jdbc.execute("TRUNCATE TABLE registro_carbono, accion_historial, "
                + "orden_mantenimiento, equipo, area RESTART IDENTITY");
        Integer area = jdbc.queryForObject(
                "INSERT INTO area (nombre, descripcion) VALUES ('Energia', 'Pruebas') "
                        + "RETURNING id_area", Integer.class);
        assertNotNull(area);
        idArea = area;

        estado = new EstadoEnMemoria(ejecutor, objectMapper);
        estado.recargar();
        equipos = new EquipoService(ejecutor, estado, objectMapper);
        ordenes = new OrdenService(ejecutor, estado, objectMapper);
    }

    @Test
    void bajaSinOrdenesMarcaInactivoYDeshacerReactivaElMismoRegistro() {
        Equipo equipo = crearEquipo("EQ-SIN-ORDENES");
        long id = equipo.getId();

        equipos.eliminar("EQ-SIN-ORDENES");

        assertTrue(equipos.listar().isEmpty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> equipos.obtener("EQ-SIN-ORDENES"));
        assertFalse(jdbc.queryForObject(
                "SELECT activo FROM equipo WHERE id_equipo = ?", Boolean.class, id));
        assertEquals(1, contar("SELECT count(*) FROM accion_historial "
                + "WHERE tipo = 'BAJA_EQUIPO' AND NOT deshecha"));
        assertEquals(1, ordenes.historial().size());

        AccionDTO accion = ordenes.deshacer();

        assertEquals("BAJA_EQUIPO", accion.tipo());
        assertEquals(0, contar("SELECT count(*) FROM accion_historial WHERE NOT deshecha"));
        assertNotNull(equipos.obtener("EQ-SIN-ORDENES"));
        assertEquals(id, equipos.obtener("EQ-SIN-ORDENES").getId());
        assertTrue(jdbc.queryForObject(
                "SELECT activo FROM equipo WHERE id_equipo = ?", Boolean.class, id));
    }

    @Test
    void bajaEliminaSoloPendientesYConservaCerradasYCanceladas() {
        Equipo equipo = crearEquipo("EQ-CON-HISTORIAL");
        Equipo otro = crearEquipo("EQ-OTRO");

        OrdenMantenimiento pendiente = crearOrden("EQ-CON-HISTORIAL", "CORRECTIVO", 1, 1, true);
        OrdenMantenimiento cerrada = crearOrden("EQ-CON-HISTORIAL", "PREVENTIVO", 2, 2, true);
        OrdenMantenimiento cancelada = crearOrden("EQ-CON-HISTORIAL", "CORRECTIVO", 2, 2, false);
        OrdenMantenimiento masPrioritaria = crearOrden("EQ-OTRO", "CORRECTIVO", 3, 3, false);
        ordenes.cerrar(cerrada.getId());
        ordenes.cancelar(cancelada.getId());

        equipos.eliminar("EQ-CON-HISTORIAL");

        assertEquals(0, contar("SELECT count(*) FROM orden_mantenimiento WHERE id_orden = ?",
                pendiente.getId()));
        assertEquals("CERRADA", estadoEnBase(cerrada.getId()));
        assertEquals("CANCELADA", estadoEnBase(cancelada.getId()));
        assertNotNull(fechaCierreEnBase(cerrada.getId()));
        assertNotNull(fechaCierreEnBase(cancelada.getId()));
        assertEquals("PENDIENTE", estadoEnBase(masPrioritaria.getId()));

        // La posicion 0 corresponde a la orden mas prioritaria del otro equipo.
        assertEquals("1", jdbc.queryForObject(
                "SELECT snapshot -> 'ordenesEliminadas' -> 0 ->> 'posicionCola' "
                        + "FROM accion_historial WHERE tipo = 'BAJA_EQUIPO'", String.class));

        ordenes.deshacer();

        OrdenMantenimiento restaurada = ordenes.obtener(pendiente.getId());
        assertEquals(Estado.PENDIENTE, restaurada.getEstado());
        assertEquals(pendiente.getConsecutivo(), restaurada.getConsecutivo());
        assertEquals(pendiente.getFechaCreacion(), restaurada.getFechaCreacion());
        assertEquals(equipo.getId(), restaurada.getIdEquipo());
        assertEquals("EQ-CON-HISTORIAL", restaurada.getCodigoEquipo());
        assertNull(restaurada.getFechaCierre());

        assertEquals("CERRADA", estadoEnBase(cerrada.getId()));
        assertEquals("CANCELADA", estadoEnBase(cancelada.getId()));
        assertEquals(3, contar("SELECT count(*) FROM orden_mantenimiento WHERE codigo_equipo = ?",
                "EQ-CON-HISTORIAL"));
        assertTrue(equipos.listar().stream().anyMatch(e -> e.getCodigo().equals("EQ-CON-HISTORIAL")));
        assertEquals(masPrioritaria.getId(), ordenes.cola().get(0).getId());

        OrdenMantenimiento nueva = crearOrden("EQ-CON-HISTORIAL", "PREVENTIVO", 1, 1, true);
        assertTrue(nueva.getId() > Math.max(pendiente.getId(), cerrada.getId()));
        assertEquals(1, contar("SELECT count(*) FROM orden_mantenimiento WHERE id_orden = ?",
                nueva.getId()));
    }

    @Test
    void deshacerFuncionaDespuesDeReiniciarElServidor() {
        crearEquipo("EQ-REINICIO");
        OrdenMantenimiento pendiente = crearOrden("EQ-REINICIO", "CORRECTIVO", 3, 2, false);
        equipos.eliminar("EQ-REINICIO");

        // Simula un proceso nuevo: reconstruye AVL, cola y pila desde PostgreSQL.
        EstadoEnMemoria estadoReiniciado = new EstadoEnMemoria(ejecutor, objectMapper);
        estadoReiniciado.asegurarCargado();
        OrdenService ordenesReiniciados =
                new OrdenService(ejecutor, estadoReiniciado, objectMapper);

        assertEquals(2, estadoReiniciado.getHistorial().tamano());
        assertEquals("BAJA_EQUIPO", estadoReiniciado.getHistorial().verTope().getTipo().name());
        assertNull(estadoReiniciado.getEquipos().buscar("EQ-REINICIO"));

        AccionDTO accion = ordenesReiniciados.deshacer();

        assertEquals("BAJA_EQUIPO", accion.tipo());
        assertNotNull(estadoReiniciado.getEquipos().buscar("EQ-REINICIO"));
        assertEquals(pendiente.getId(), estadoReiniciado.getOrdenesPendientes().verPrimera().getId());
        assertEquals(1, contar("SELECT count(*) FROM accion_historial WHERE deshecha"));

        EstadoEnMemoria estadoFinal = new EstadoEnMemoria(ejecutor, objectMapper);
        estadoFinal.asegurarCargado();
        assertNotNull(estadoFinal.getEquipos().buscar("EQ-REINICIO"));
        assertNotNull(estadoFinal.getOrdenesPendientes().buscarPorId(pendiente.getId()));
    }

    @Test
    void codigoUnicoSeReactivaSinDuplicarLaIdentidad() {
        Equipo original = crearEquipo("EQ-UNICO");
        long id = original.getId();
        equipos.eliminar("EQ-UNICO");

        EquipoService.ResultadoCreacion resultado = equipos.crear(
                new EquipoRequest("EQ-UNICO", "Equipo reactivado", idArea));

        assertTrue(resultado.reactivado());
        assertEquals(id, resultado.equipo().getId());
        assertEquals("Equipo reactivado", resultado.equipo().getNombre());
        assertEquals(1, contar("SELECT count(*) FROM equipo WHERE codigo = 'EQ-UNICO'"));
        assertTrue(resultado.equipo().isActivo());

        try (Connection conexion = dataSource.getConnection();
             PreparedStatement ps = conexion.prepareStatement(
                     "INSERT INTO equipo (codigo, nombre, id_area) VALUES (?, ?, ?)")) {
            ps.setString(1, "EQ-UNICO");
            ps.setString(2, "Duplicado");
            ps.setInt(3, idArea);
            assertThrows(SQLException.class, ps::executeUpdate);
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void reconstruyeElAvlSoloConEquiposActivos() {
        crearEquipo("EQ-ACTIVO");
        Equipo inactivo = crearEquipo("EQ-INACTIVO");
        jdbc.update("UPDATE equipo SET activo = FALSE WHERE id_equipo = ?", inactivo.getId());

        EstadoEnMemoria reconstruido = new EstadoEnMemoria(ejecutor, objectMapper);
        reconstruido.asegurarCargado();

        assertNotNull(reconstruido.getEquipos().buscar("EQ-ACTIVO"));
        assertNull(reconstruido.getEquipos().buscar("EQ-INACTIVO"));
        assertEquals(1, reconstruido.getEquipos().tamano());
    }

    @Test
    void unaFallaTransaccionalNoDejaBajaParcialNiAccionEnLaPila() {
        Equipo equipo = crearEquipo("EQ-FALLO");
        OrdenMantenimiento pendiente = crearOrden("EQ-FALLO", "CORRECTIVO", 3, 3, false);

        jdbc.execute("""
                CREATE FUNCTION bloquear_baja_prueba() RETURNS trigger AS $$
                BEGIN
                    IF NEW.activo = FALSE AND OLD.activo = TRUE
                       AND OLD.codigo = 'EQ-FALLO' THEN
                        RAISE EXCEPTION 'fallo simulado en la baja';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER trg_bloquear_baja_prueba BEFORE UPDATE ON equipo "
                + "FOR EACH ROW EXECUTE FUNCTION bloquear_baja_prueba()");
        try {
            assertThrows(ErrorBaseDatosException.class, () -> equipos.eliminar("EQ-FALLO"));
        } finally {
            jdbc.execute("DROP TRIGGER trg_bloquear_baja_prueba ON equipo");
            jdbc.execute("DROP FUNCTION bloquear_baja_prueba()");
        }

        assertTrue(jdbc.queryForObject(
                "SELECT activo FROM equipo WHERE id_equipo = ?", Boolean.class, equipo.getId()));
        assertEquals(1, contar("SELECT count(*) FROM orden_mantenimiento WHERE id_orden = ?",
                pendiente.getId()));
        assertEquals(0, contar("SELECT count(*) FROM accion_historial "
                + "WHERE tipo = 'BAJA_EQUIPO'"));
        assertNotNull(estado.getEquipos().buscar("EQ-FALLO"));
        assertNotNull(estado.getOrdenesPendientes().buscarPorId(pendiente.getId()));
    }

    @Test
    void dosProcesosNoRestauranDosVecesLaMismaAccion() throws Exception {
        Equipo equipo = crearEquipo("EQ-CONCURRENTE");
        Long idPendiente = jdbc.queryForObject(
                "INSERT INTO orden_mantenimiento (id_equipo, codigo_equipo, tipo, descripcion, "
                        + "impacto_produccion, riesgo_seguridad, hay_respaldo) "
                        + "VALUES (?, 'EQ-CONCURRENTE', 'CORRECTIVO', 'Descripcion', 3, 3, FALSE) "
                        + "RETURNING id_orden",
                Long.class, equipo.getId());
        assertNotNull(idPendiente);
        equipos.eliminar("EQ-CONCURRENTE");

        EstadoEnMemoria estadoA = new EstadoEnMemoria(ejecutor, objectMapper);
        EstadoEnMemoria estadoB = new EstadoEnMemoria(ejecutor, objectMapper);
        estadoA.asegurarCargado();
        estadoB.asegurarCargado();
        OrdenService servicioA = new OrdenService(ejecutor, estadoA, objectMapper);
        OrdenService servicioB = new OrdenService(ejecutor, estadoB, objectMapper);

        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> deshacerA = () -> {
                inicio.await(5, TimeUnit.SECONDS);
                try {
                    return servicioA.deshacer();
                } catch (RecursoNoEncontradoException e) {
                    return e;
                }
            };
            Callable<Object> deshacerB = () -> {
                inicio.await(5, TimeUnit.SECONDS);
                try {
                    return servicioB.deshacer();
                } catch (RecursoNoEncontradoException e) {
                    return e;
                }
            };
            Future<Object> resultadoA = pool.submit(deshacerA);
            Future<Object> resultadoB = pool.submit(deshacerB);
            inicio.countDown();

            Object a = resultadoA.get(10, TimeUnit.SECONDS);
            Object b = resultadoB.get(10, TimeUnit.SECONDS);

            int exitos = (a instanceof AccionDTO ? 1 : 0) + (b instanceof AccionDTO ? 1 : 0);
            int noEncontradas = (a instanceof RecursoNoEncontradoException ? 1 : 0)
                    + (b instanceof RecursoNoEncontradoException ? 1 : 0);
            assertEquals(1, exitos);
            assertEquals(1, noEncontradas);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, contar("SELECT count(*) FROM orden_mantenimiento WHERE id_orden = ?",
                idPendiente));
        assertEquals(1, contar("SELECT count(*) FROM accion_historial WHERE deshecha"));
    }

    private static void ejecutarScript(Connection conexion, String recurso) throws Exception {
        String sql;
        try (var entrada = new ClassPathResource(recurso).getInputStream()) {
            sql = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        }
    }

    private Equipo crearEquipo(String codigo) {
        return equipos.crear(new EquipoRequest(codigo, "Equipo " + codigo, idArea)).equipo();
    }

    private OrdenMantenimiento crearOrden(String codigoEquipo, String tipo, int impacto,
                                          int riesgo, boolean respaldo) {
        return ordenes.crear(new OrdenRequest(codigoEquipo, tipo, "Descripcion de prueba",
                impacto, riesgo, respaldo));
    }

    private int contar(String sql, Object... argumentos) {
        Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
        return total == null ? 0 : total;
    }

    private String estadoEnBase(long idOrden) {
        return jdbc.queryForObject(
                "SELECT estado FROM orden_mantenimiento WHERE id_orden = ?", String.class, idOrden);
    }

    private LocalDateTime fechaCierreEnBase(long idOrden) {
        return jdbc.queryForObject(
                "SELECT fecha_cierre FROM orden_mantenimiento WHERE id_orden = ?",
                (rs, fila) -> rs.getObject(1, LocalDateTime.class), idOrden);
    }
}
