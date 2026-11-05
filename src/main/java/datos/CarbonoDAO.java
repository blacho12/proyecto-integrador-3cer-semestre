package datos;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import modelo.RegistroCarbono;

/**
 * Acceso a datos de registro_carbono y configuracion_planta.
 * Todas las sentencias usan PreparedStatement; los calculos se reciben ya resueltos
 * por CalculadoraCarbono y aqui solo se persisten.
 */
public class CarbonoDAO {

    /** Valor usado solo si la fila de configuracion no existe en la base. */
    public static final BigDecimal FACTOR_POR_DEFECTO = new BigDecimal("0.1500");

    private static final String COLUMNAS =
            "r.id_registro, r.codigo_equipo, e.nombre AS nombre_equipo, "
            + "a.nombre AS nombre_area, r.horas_mal_estado, r.consumo_adicional_kwh, "
            + "r.emisiones_kg_co2, r.factor_emision, r.fecha_registro";

    private final Connection conexion;

    public CarbonoDAO(Connection conexion) { this.conexion = conexion; }

    /** Inserta un registro historico y devuelve el objeto con id y fecha generados por PostgreSQL. */
    public RegistroCarbono insertar(String codigoEquipo, String nombreEquipo, String nombreArea,
                                    BigDecimal horasMalEstado, BigDecimal consumoAdicionalKwh,
                                    BigDecimal emisionesKgCo2, BigDecimal factorEmision)
            throws SQLException {
        String sql = "INSERT INTO registro_carbono "
                   + "(codigo_equipo, horas_mal_estado, consumo_adicional_kwh, "
                   + "emisiones_kg_co2, factor_emision) "
                   + "VALUES (?, ?, ?, ?, ?) "
                   + "RETURNING id_registro, fecha_registro";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigoEquipo);
            ps.setBigDecimal(2, horasMalEstado);
            ps.setBigDecimal(3, consumoAdicionalKwh);
            ps.setBigDecimal(4, emisionesKgCo2);
            ps.setBigDecimal(5, factorEmision);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("El INSERT de registro_carbono no devolvio los campos generados");
                }
                LocalDateTime fecha = rs.getObject("fecha_registro", LocalDateTime.class);
                return new RegistroCarbono(rs.getLong("id_registro"), codigoEquipo, nombreEquipo,
                        nombreArea, horasMalEstado, consumoAdicionalKwh, emisionesKgCo2,
                        factorEmision, fecha);
            }
        }
    }

    /** Historico completo, del registro mas reciente al mas antiguo. */
    public List<RegistroCarbono> consultarTodos() throws SQLException {
        List<RegistroCarbono> registros = new ArrayList<>();
        String sql = "SELECT " + COLUMNAS + " "
                   + "FROM registro_carbono r "
                   + "JOIN equipo e ON e.codigo = r.codigo_equipo "
                   + "JOIN area a ON a.id_area = e.id_area "
                   + "ORDER BY r.fecha_registro DESC, r.id_registro DESC";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) registros.add(mapear(rs));
        }
        return registros;
    }

    /** Factor de emision electrica configurado para la planta (kg CO2 por kWh). */
    public BigDecimal consultarFactorEmision() throws SQLException {
        String sql = "SELECT factor_emision FROM configuracion_planta WHERE id_configuracion = 1";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBigDecimal(1) : FACTOR_POR_DEFECTO;
        }
    }

    /** Crea o actualiza la unica fila de configuracion de la planta. */
    public boolean actualizarFactorEmision(BigDecimal factorEmision) throws SQLException {
        String sql = "INSERT INTO configuracion_planta (id_configuracion, factor_emision) "
                   + "VALUES (1, ?) "
                   + "ON CONFLICT (id_configuracion) "
                   + "DO UPDATE SET factor_emision = EXCLUDED.factor_emision";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setBigDecimal(1, factorEmision);
            return ps.executeUpdate() > 0;
        }
    }

    private RegistroCarbono mapear(ResultSet rs) throws SQLException {
        return new RegistroCarbono(
                rs.getLong("id_registro"),
                rs.getString("codigo_equipo"),
                rs.getString("nombre_equipo"),
                rs.getString("nombre_area"),
                rs.getBigDecimal("horas_mal_estado"),
                rs.getBigDecimal("consumo_adicional_kwh"),
                rs.getBigDecimal("emisiones_kg_co2"),
                rs.getBigDecimal("factor_emision"),
                rs.getObject("fecha_registro", LocalDateTime.class));
    }
}
