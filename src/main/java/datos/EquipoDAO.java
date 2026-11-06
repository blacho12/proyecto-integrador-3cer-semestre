package datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import modelo.Equipo;

/** Acceso a datos de la tabla equipo. Todas las consultas usan PreparedStatement. */
public class EquipoDAO {
    private final Connection conexion;

    public EquipoDAO(Connection conexion) { this.conexion = conexion; }

    /**
     * Inserta un equipo. PostgreSQL rechaza la operacion (SQLException, SQLState 23503)
     * si el id del area no existe, respetando la clave foranea fk_equipo_area.
     */
    public Equipo insertar(Equipo equipo) throws SQLException {
        String sql = "INSERT INTO equipo (codigo, nombre, id_area) VALUES (?, ?, ?) "
                   + "RETURNING id_equipo, activo, consumo_normal_kwh, porcentaje_extra";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, equipo.getCodigo());
            ps.setString(2, equipo.getNombre());
            ps.setInt(3, equipo.getIdArea());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("El INSERT del equipo no devolvio el id generado.");
                return new Equipo(rs.getLong("id_equipo"), equipo.getCodigo(), equipo.getNombre(),
                        equipo.getIdArea(), rs.getBigDecimal("consumo_normal_kwh"),
                        rs.getBigDecimal("porcentaje_extra"), rs.getBoolean("activo"));
            }
        }
    }

    public List<Equipo> consultarTodos() throws SQLException {
        List<Equipo> equipos = new ArrayList<>();
        String sql = "SELECT id_equipo, codigo, nombre, id_area, consumo_normal_kwh, "
                   + "porcentaje_extra, activo FROM equipo WHERE activo ORDER BY codigo";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) equipos.add(mapear(rs));
        }
        return equipos;
    }

    /** Incluye los equipos inactivos; se usa para resolver el area de ordenes historicas. */
    public List<Equipo> consultarTodosIncluyendoInactivos() throws SQLException {
        List<Equipo> equipos = new ArrayList<>();
        String sql = "SELECT id_equipo, codigo, nombre, id_area, consumo_normal_kwh, "
                   + "porcentaje_extra, activo FROM equipo ORDER BY codigo";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) equipos.add(mapear(rs));
        }
        return equipos;
    }

    /** Devuelve el equipo con ese codigo o null si no existe. */
    public Equipo consultarPorCodigo(String codigo) throws SQLException {
        String sql = "SELECT id_equipo, codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra, activo "
                   + "FROM equipo WHERE codigo = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Equipo consultarActivoPorCodigoParaActualizar(String codigo) throws SQLException {
        String sql = "SELECT id_equipo, codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra, activo "
                   + "FROM equipo WHERE codigo = ? AND activo FOR UPDATE";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Equipo consultarPorCodigoParaActualizar(String codigo) throws SQLException {
        String sql = "SELECT id_equipo, codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra, activo "
                   + "FROM equipo WHERE codigo = ? FOR UPDATE";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Equipo consultarPorIdParaActualizar(long idEquipo) throws SQLException {
        String sql = "SELECT id_equipo, codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra, activo "
                   + "FROM equipo WHERE id_equipo = ? FOR UPDATE";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, idEquipo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Actualiza nombre y area; el codigo es la clave y no cambia. Devuelve false si no existe. */
    public boolean actualizar(Equipo equipo) throws SQLException {
        String sql = "UPDATE equipo SET nombre = ?, id_area = ? WHERE codigo = ? AND activo";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, equipo.getNombre());
            ps.setInt(2, equipo.getIdArea());
            ps.setString(3, equipo.getCodigo());
            return ps.executeUpdate() > 0;
        }
    }

    /** Actualiza exclusivamente los parametros energeticos del equipo. */
    public boolean actualizarParametrosCarbono(String codigo, java.math.BigDecimal consumoNormalKwh,
                                                java.math.BigDecimal porcentajeExtra) throws SQLException {
        String sql = "UPDATE equipo SET consumo_normal_kwh = ?, porcentaje_extra = ? "
                   + "WHERE codigo = ? AND activo";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setBigDecimal(1, consumoNormalKwh);
            ps.setBigDecimal(2, porcentajeExtra);
            ps.setString(3, codigo);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean marcarInactivo(long idEquipo) throws SQLException {
        String sql = "UPDATE equipo SET activo = FALSE WHERE id_equipo = ? AND activo";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, idEquipo);
            return ps.executeUpdate() > 0;
        }
    }

    public Equipo reactivar(long idEquipo, String nombre, int idArea) throws SQLException {
        String sql = "UPDATE equipo SET nombre = ?, id_area = ?, activo = TRUE "
                   + "WHERE id_equipo = ? AND NOT activo "
                   + "RETURNING id_equipo, codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra, activo";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setInt(2, idArea);
            ps.setLong(3, idEquipo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public boolean restaurar(Equipo equipo) throws SQLException {
        String sql = "UPDATE equipo SET nombre = ?, id_area = ?, consumo_normal_kwh = ?, "
                   + "porcentaje_extra = ?, activo = TRUE WHERE id_equipo = ? AND NOT activo";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, equipo.getNombre());
            ps.setInt(2, equipo.getIdArea());
            ps.setBigDecimal(3, equipo.getConsumoNormalKwh());
            ps.setBigDecimal(4, equipo.getPorcentajeExtra());
            ps.setLong(5, equipo.getId());
            return ps.executeUpdate() == 1;
        }
    }

    private Equipo mapear(ResultSet rs) throws SQLException {
        return new Equipo(rs.getLong("id_equipo"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getInt("id_area"), rs.getBigDecimal("consumo_normal_kwh"),
                rs.getBigDecimal("porcentaje_extra"), rs.getBoolean("activo"));
    }
}
