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

/** Acceso JDBC a la configuracion y al historial persistido de carbono. */
public class CarbonoDAO {

    private static final String COLUMNAS =
            "r.id_registro, r.codigo_equipo, e.nombre AS nombre_equipo, "
            + "a.nombre AS nombre_area, r.horas_mal_estado, r.consumo_adicional_kwh, "
            + "r.emisiones_kg_co2, r.factor_emision, r.fecha_registro";

    private final Connection conexion;

    public CarbonoDAO(Connection conexion) {
        this.conexion = conexion;
    }

    public BigDecimal consultarFactorEmision() throws SQLException {
        String sql = "SELECT factor_emision FROM configuracion_planta WHERE id_configuracion = 1";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException(
                        "Falta la configuracion de emision de carbono. Aplique la migracion esquema_carbono.sql.",
                        "55000");
            }
            return rs.getBigDecimal("factor_emision");
        }
    }

    public void actualizarFactorEmision(BigDecimal factorEmision) throws SQLException {
        String sql = "UPDATE configuracion_planta SET factor_emision = ? WHERE id_configuracion = 1";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setBigDecimal(1, factorEmision);
            if (ps.executeUpdate() == 0) {
                throw new SQLException(
                        "Falta la configuracion de emision de carbono. Aplique la migracion esquema_carbono.sql.",
                        "55000");
            }
        }
    }

    public RegistroCarbono insertar(long idEquipo, String codigoEquipo, BigDecimal horasMalEstado,
                                    BigDecimal consumoAdicionalKwh, BigDecimal emisionesKgCo2,
                                    BigDecimal factorEmision) throws SQLException {
        String sql = "INSERT INTO registro_carbono "
                   + "(id_equipo, codigo_equipo, horas_mal_estado, consumo_adicional_kwh, "
                   + "emisiones_kg_co2, factor_emision) "
                   + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id_registro";
        long id;
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, idEquipo);
            ps.setString(2, codigoEquipo);
            ps.setBigDecimal(3, horasMalEstado);
            ps.setBigDecimal(4, consumoAdicionalKwh);
            ps.setBigDecimal(5, emisionesKgCo2);
            ps.setBigDecimal(6, factorEmision);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("El INSERT de registro_carbono no devolvio el id generado.");
                }
                id = rs.getLong("id_registro");
            }
        }
        return consultarPorId(id);
    }

    public List<RegistroCarbono> consultarTodos() throws SQLException {
        List<RegistroCarbono> registros = new ArrayList<>();
        String sql = "SELECT " + COLUMNAS + " "
                   + "FROM registro_carbono r "
                   + "JOIN equipo e ON e.id_equipo = r.id_equipo "
                   + "JOIN area a ON a.id_area = e.id_area "
                   + "ORDER BY r.fecha_registro DESC, r.id_registro DESC";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                registros.add(mapear(rs));
            }
        }
        return registros;
    }

    private RegistroCarbono consultarPorId(long idRegistro) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " "
                   + "FROM registro_carbono r "
                   + "JOIN equipo e ON e.id_equipo = r.id_equipo "
                   + "JOIN area a ON a.id_area = e.id_area "
                   + "WHERE r.id_registro = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, idRegistro);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No se pudo consultar el registro de carbono recien creado.");
                }
                return mapear(rs);
            }
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
