package app;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import datos.ConexionBD;
import modelo.Area;
import modelo.Equipo;
import modelo.RegistroCarbono;
import modelo.ResumenCarbono;

/**
 * Prueba de integracion de las dos funcionalidades nuevas (requiere PostgreSQL y las
 * variables DB_URL, DB_USER y DB_PASSWORD).
 *
 * Cubre: formula del ejemplo, validacion de negativos, configuracion de parametros,
 * registros por equipo, maquina sin registros, consolidacion por area y de la planta,
 * trazabilidad del factor, persistencia tras reiniciar y CRUD completo de areas
 * (incluido el bloqueo del borrado con equipos asociados y los nombres duplicados).
 * Limpia sus propios datos al empezar y al terminar.
 */
public class PruebaCarbonoAreas {

    private static final String AREA_1 = "Area de pruebas carbono";
    private static final String AREA_1_EDITADA = AREA_1 + " editada";
    private static final String AREA_2 = "Area de pruebas carbono 2";
    private static final String EQ_A = "EQ-CB-001";
    private static final String EQ_B = "EQ-CB-002";
    private static final String EQ_C = "EQ-CB-003";

    private static int aciertos;
    private static int fallas;

    public static void main(String[] args) {
        System.out.println("=== PRUEBAS DE HUELLA DE CARBONO Y GESTION DE AREAS ===");

        System.out.println("\n--- 1. Formula de CalculadoraCarbono (sin base de datos) ---");
        probarFormula();

        GestorOrdenes gestor;
        try {
            gestor = new GestorOrdenes();
        } catch (SQLException e) {
            System.out.println("FALLO | Conexion a PostgreSQL: " + e.getMessage());
            System.out.println("Configure DB_URL, DB_USER y DB_PASSWORD.");
            System.exit(1);
            return;
        }

        BigDecimal factorOriginal = null;
        try {
            factorOriginal = gestor.consultarFactorEmision();
            System.out.println("\n--- Limpieza de datos de ejecuciones anteriores ---");
            limpiar(gestor);

            probarAreas(gestor);
            probarCarbono(gestor);
        } catch (SQLException e) {
            fallas++;
            System.out.println("FALLO | Error inesperado en PostgreSQL: "
                    + ConexionBD.mensajeAmigable(e));
        } finally {
            System.out.println("\n--- Limpieza final ---");
            limpiar(gestor);
            if (factorOriginal != null) {
                gestor.actualizarFactorEmision(factorOriginal);
            }
            gestor.cerrar();
        }

        System.out.println("\n=== RESUMEN: " + aciertos + " OK, " + fallas + " FALLAS ===");
        if (fallas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ formula

    private static void probarFormula() {
        CalculadoraCarbono.Resultado ejemplo = CalculadoraCarbono.calcular(
                new BigDecimal("10"), new BigDecimal("8"),
                new BigDecimal("15"), new BigDecimal("0.15"));
        verificar("1.1 Ejemplo: consumo adicional = 12 kWh",
                ejemplo.getConsumoAdicionalKwh().compareTo(new BigDecimal("12.0000")) == 0);
        verificar("1.2 Ejemplo: emisiones = 1.8 kg CO2",
                ejemplo.getEmisionesKgCo2().compareTo(new BigDecimal("1.8000")) == 0);

        rechazar("1.3 Rechaza consumo normal negativo", () -> CalculadoraCarbono.calcular(
                new BigDecimal("-1"), new BigDecimal("8"),
                new BigDecimal("15"), new BigDecimal("0.15")));
        rechazar("1.4 Rechaza horas negativas", () -> CalculadoraCarbono.calcular(
                new BigDecimal("10"), new BigDecimal("-1"),
                new BigDecimal("15"), new BigDecimal("0.15")));
        rechazar("1.5 Rechaza porcentaje negativo", () -> CalculadoraCarbono.calcular(
                new BigDecimal("10"), new BigDecimal("8"),
                new BigDecimal("-15"), new BigDecimal("0.15")));
        rechazar("1.6 Rechaza factor de emision negativo", () -> CalculadoraCarbono.calcular(
                new BigDecimal("10"), new BigDecimal("8"),
                new BigDecimal("15"), new BigDecimal("-0.15")));
    }

    // ------------------------------------------------------------------ areas

    private static void probarAreas(GestorOrdenes gestor) throws SQLException {
        System.out.println("\n--- 2. Gestion completa de areas ---");

        Area area1 = gestor.registrarArea(AREA_1, "Zona de prueba");
        verificar("2.1 Crear area con id generado", area1 != null && area1.getIdArea() > 0);
        verificar("2.2 Rechazar nombre vacio",
                gestor.registrarArea("   ", "sin nombre") == null);
        verificar("2.3 Rechazar nombre duplicado",
                gestor.registrarArea(AREA_1, "duplicada") == null);
        verificar("2.3 Rechazar duplicado ignorando mayusculas",
                gestor.registrarArea(AREA_1.toUpperCase(), "duplicada") == null);

        Area area2 = gestor.registrarArea(AREA_2, "Area temporal para eliminar");
        verificar("2.4 Crear segunda area", area2 != null && area2.getIdArea() > 0);

        verificar("2.5 Modificar nombre y descripcion del area",
                gestor.modificarArea(area1.getIdArea(), AREA_1_EDITADA, "Descripcion editada"));
        Area editada = gestor.getAreaDAO().consultarPorId(area1.getIdArea());
        verificar("2.5 Los cambios se guardan en PostgreSQL", editada != null
                && AREA_1_EDITADA.equals(editada.getNombre())
                && "Descripcion editada".equals(editada.getDescripcion()));
        verificar("2.6 Rechazar un nombre que ya usa otra area",
                !gestor.modificarArea(area1.getIdArea(), AREA_2, "x"));

        verificar("2.7 Eliminar un area sin equipos", gestor.eliminarArea(area2.getIdArea()));
        verificar("2.7 El area eliminada ya no existe",
                gestor.getAreaDAO().consultarPorId(area2.getIdArea()) == null);

        verificar("2.8 Registrar equipos en el area",
                gestor.registrarEquipo(new Equipo(EQ_A, "Motor de prueba A", area1.getIdArea())) != null
                        && gestor.registrarEquipo(new Equipo(EQ_B, "Bomba de prueba B", area1.getIdArea())) != null);
        verificar("2.9 No se elimina un area con equipos asociados",
                !gestor.eliminarArea(area1.getIdArea()));
        verificar("2.9 El mensaje explica que hay equipos asociados",
                gestor.getUltimoError() != null
                        && gestor.getUltimoError().toLowerCase().contains("equipo"));
        verificar("2.9 El area sigue existiendo",
                gestor.getAreaDAO().consultarPorId(area1.getIdArea()) != null);

        GestorOrdenes reinicio = new GestorOrdenes();
        Area persistida = reinicio.getAreaDAO().consultarPorId(area1.getIdArea());
        verificar("2.10 El area modificada persiste tras reiniciar la aplicacion",
                persistida != null && AREA_1_EDITADA.equals(persistida.getNombre()));
        reinicio.cerrar();
    }

    // ------------------------------------------------------------------ carbono

    private static void probarCarbono(GestorOrdenes gestor) throws SQLException {
        System.out.println("\n--- 3. Parametros por equipo y validaciones ---");
        verificar("3.1 Configurar equipo A: 10 kWh/h y 15% extra",
                gestor.configurarParametrosEquipo(EQ_A, new BigDecimal("10"), new BigDecimal("15")));
        verificar("3.1 Configurar equipo B: 20 kWh/h y 10% extra",
                gestor.configurarParametrosEquipo(EQ_B, new BigDecimal("20"), new BigDecimal("10")));
        verificar("3.2 Rechazar consumo normal negativo",
                !gestor.configurarParametrosEquipo(EQ_A, new BigDecimal("-10"), new BigDecimal("15")));
        verificar("3.2 Rechazar porcentaje negativo",
                !gestor.configurarParametrosEquipo(EQ_A, new BigDecimal("10"), new BigDecimal("-5")));
        verificar("3.2 Rechazar porcentaje mayor que 100",
                !gestor.configurarParametrosEquipo(EQ_A, new BigDecimal("10"), new BigDecimal("150")));
        verificar("3.2 Rechazar factor de emision negativo",
                !gestor.actualizarFactorEmision(new BigDecimal("-0.1")));

        Equipo equipoA = gestor.getEquipoDAO().consultarPorCodigo(EQ_A);
        verificar("3.2 Los rechazos no alteran los parametros guardados",
                equipoA != null
                        && equipoA.getConsumoNormalKwh().compareTo(new BigDecimal("10.00")) == 0
                        && equipoA.getPorcentajeExtra().compareTo(new BigDecimal("15.00")) == 0);

        verificar("3.3 Configurar factor de emision de la planta en 0.15 kg CO2/kWh",
                gestor.actualizarFactorEmision(new BigDecimal("0.15")));

        Area areaC = gestor.registrarArea(AREA_2, "Area de la maquina sin registros");
        verificar("3.4 Crear area para una maquina sin registros", areaC != null);
        verificar("3.4 Crear equipo C sin registros de carbono",
                gestor.registrarEquipo(new Equipo(EQ_C, "Compresor de prueba C", areaC.getIdArea())) != null);
        verificar("3.4 Configurar equipo C: 5 kWh/h y 20% extra",
                gestor.configurarParametrosEquipo(EQ_C, new BigDecimal("5"), new BigDecimal("20")));

        ResumenCarbono base = gestor.calcularResumenCarbono();
        BigDecimal totalBase = base.getTotalEmisiones();
        int equiposBase = base.getEquiposConEmisiones();
        int registrosBase = base.getRegistros().size();
        verificar("3.5 Una maquina sin registros no genera emisiones",
                !base.getEmisionesPorEquipo().containsKey(EQ_C));

        System.out.println("\n--- 4. Calculo y registro de emisiones ---");
        RegistroCarbono registroA1 = gestor.registrarEmisionCarbono(EQ_A, new BigDecimal("8"));
        verificar("4.1 Equipo A, 8 h: consumo adicional = 12 kWh", registroA1 != null
                && registroA1.getConsumoAdicionalKwh().compareTo(new BigDecimal("12.0000")) == 0);
        verificar("4.1 Equipo A, 8 h: emisiones = 1.8 kg CO2", registroA1 != null
                && registroA1.getEmisionesKgCo2().compareTo(new BigDecimal("1.8000")) == 0);
        verificar("4.1 El registro conserva el factor utilizado", registroA1 != null
                && registroA1.getFactorEmision().compareTo(new BigDecimal("0.1500")) == 0);
        verificar("4.2 Rechazar horas negativas",
                gestor.registrarEmisionCarbono(EQ_A, new BigDecimal("-2")) == null);

        RegistroCarbono registroB = gestor.registrarEmisionCarbono(EQ_B, new BigDecimal("5"));
        verificar("4.3 Equipo B, 5 h: 10 kWh y 1.5 kg CO2", registroB != null
                && registroB.getConsumoAdicionalKwh().compareTo(new BigDecimal("10.0000")) == 0
                && registroB.getEmisionesKgCo2().compareTo(new BigDecimal("1.5000")) == 0);

        RegistroCarbono registroA2 = gestor.registrarEmisionCarbono(EQ_A, new BigDecimal("2"));
        verificar("4.4 Equipo A, 2 h: 3 kWh y 0.45 kg CO2", registroA2 != null
                && registroA2.getConsumoAdicionalKwh().compareTo(new BigDecimal("3.0000")) == 0
                && registroA2.getEmisionesKgCo2().compareTo(new BigDecimal("0.4500")) == 0);

        System.out.println("\n--- 5. Indicadores consolidados ---");
        ResumenCarbono resumen = gestor.calcularResumenCarbono();
        verificar("5.1 Total adicional de toda la planta = 3.75 kg CO2",
                resumen.getTotalEmisiones().subtract(totalBase)
                        .compareTo(new BigDecimal("3.7500")) == 0);
        verificar("5.2 Emisiones adicionales del equipo A = 2.25 kg CO2",
                valorEquipo(resumen, EQ_A).compareTo(new BigDecimal("2.2500")) == 0);
        verificar("5.2 Emisiones adicionales del equipo B = 1.5 kg CO2",
                valorEquipo(resumen, EQ_B).compareTo(new BigDecimal("1.5000")) == 0);
        verificar("5.3 La maquina C sigue sin registros",
                !resumen.getEmisionesPorEquipo().containsKey(EQ_C));
        verificar("5.4 Consolidado por area = 3.75 kg CO2 (dos maquinas en la misma area)",
                valorArea(resumen, AREA_1_EDITADA).compareTo(new BigDecimal("3.7500")) == 0);
        verificar("5.5 Numero de maquinas que generaron emisiones = base + 2",
                resumen.getEquiposConEmisiones() == equiposBase + 2);
        verificar("5.6 El historial contiene los tres registros nuevos",
                resumen.getRegistros().size() == registrosBase + 3);

        System.out.println("\n--- 6. Persistencia tras reiniciar ---");
        GestorOrdenes reinicio = new GestorOrdenes();
        ResumenCarbono persistido = reinicio.calcularResumenCarbono();
        verificar("6.1 Los registros siguen disponibles despues de reiniciar",
                persistido.getTotalEmisiones().subtract(totalBase)
                        .compareTo(new BigDecimal("3.7500")) == 0);
        long registrosEquipoA = persistido.getRegistros().stream()
                .filter(r -> EQ_A.equals(r.getCodigoEquipo())).count();
        verificar("6.2 Los dos registros del equipo A persisten", registrosEquipoA == 2);
        verificar("6.3 El factor de la planta persiste",
                reinicio.consultarFactorEmision().compareTo(new BigDecimal("0.1500")) == 0);
        Area areaPersistida = reinicio.getAreaDAO().consultarPorId(
                reinicio.getAreaDAO().consultarTodas().stream()
                        .filter(a -> AREA_1_EDITADA.equals(a.getNombre()))
                        .map(Area::getIdArea).findFirst().orElse(0));
        verificar("6.4 El area modificada persiste", areaPersistida != null);
        reinicio.cerrar();

        System.out.println("\n--- 7. Trazabilidad del factor de emision ---");
        verificar("7.1 Cambiar el factor de la planta a 0.20",
                gestor.actualizarFactorEmision(new BigDecimal("0.20")));
        RegistroCarbono registroNuevo = gestor.registrarEmisionCarbono(EQ_A, BigDecimal.ONE);
        verificar("7.2 El nuevo registro usa el factor vigente", registroNuevo != null
                && registroNuevo.getFactorEmision().compareTo(new BigDecimal("0.2000")) == 0);
        verificar("7.3 Equipo A, 1 h con factor 0.20: 0.30 kg CO2", registroNuevo != null
                && registroNuevo.getEmisionesKgCo2().compareTo(new BigDecimal("0.3000")) == 0);

        ResumenCarbono conFactores = gestor.calcularResumenCarbono();
        boolean conserva015 = conFactores.getRegistros().stream()
                .anyMatch(r -> r.getFactorEmision().compareTo(new BigDecimal("0.1500")) == 0);
        boolean conserva020 = conFactores.getRegistros().stream()
                .anyMatch(r -> r.getFactorEmision().compareTo(new BigDecimal("0.2000")) == 0);
        verificar("7.4 Cada registro conserva el factor con el que fue calculado",
                conserva015 && conserva020);
    }

    // ------------------------------------------------------------------ utilidades

    private static BigDecimal valorEquipo(ResumenCarbono resumen, String codigo) {
        return resumen.getEmisionesPorEquipo().getOrDefault(codigo, BigDecimal.ZERO);
    }

    private static BigDecimal valorArea(ResumenCarbono resumen, String area) {
        return resumen.getEmisionesPorArea().getOrDefault(area, BigDecimal.ZERO);
    }

    /** Borra solo los datos creados por esta prueba, en orden respetando las claves foraneas. */
    private static void limpiar(GestorOrdenes gestor) {
        for (String codigo : new String[] {EQ_A, EQ_B, EQ_C}) {
            try (PreparedStatement ps = gestor.getConexionBD().prepareStatement(
                    "DELETE FROM registro_carbono WHERE codigo_equipo = ?")) {
                ps.setString(1, codigo);
                ps.executeUpdate();
                gestor.getConexionBD().commit();
            } catch (SQLException e) {
                rollback(gestor);
            }

            try {
                if (gestor.getEquipoDAO().eliminar(codigo)) gestor.getConexionBD().commit();
            } catch (SQLException e) {
                rollback(gestor);
            }
        }

        try {
            for (Area area : gestor.consultarAreas()) {
                String nombre = area.getNombre();
                if (nombre != null
                        && nombre.toUpperCase().startsWith(AREA_1.toUpperCase())) {
                    try {
                        if (gestor.getAreaDAO().eliminar(area.getIdArea())) {
                            gestor.getConexionBD().commit();
                        }
                    } catch (SQLException e) {
                        rollback(gestor);
                    }
                }
            }
        } catch (SQLException e) {
            rollback(gestor);
        }
    }

    private static void rollback(GestorOrdenes gestor) {
        try {
            gestor.getConexionBD().rollback();
        } catch (SQLException e) {
            /* sin conexion: la prueba no puede continuar con esta transaccion */
        }
    }

    private static void rechazar(String nombre, Runnable accion) {
        try {
            accion.run();
            fallas++;
            System.out.println("FALLO | " + nombre);
        } catch (IllegalArgumentException e) {
            aciertos++;
            System.out.println("OK    | " + nombre);
        }
    }

    private static void verificar(String nombre, boolean condicion) {
        if (condicion) {
            aciertos++;
            System.out.println("OK    | " + nombre);
        } else {
            fallas++;
            System.out.println("FALLO | " + nombre);
        }
    }
}
