package datos;

import java.math.BigDecimal;
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
    public void insertar(Equipo equipo) throws SQLException {
        String sql = "INSERT INTO equipo "
                   + "(codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, equipo.getCodigo());
            ps.setString(2, equipo.getNombre());
            ps.setInt(3, equipo.getIdArea());
            ps.setBigDecimal(4, equipo.getConsumoNormalKwh());
            ps.setBigDecimal(5, equipo.getPorcentajeExtra());
            ps.executeUpdate();
        }
    }

    public List<Equipo> consultarTodos() throws SQLException {
        List<Equipo> equipos = new ArrayList<>();
        String sql = "SELECT codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra "
                   + "FROM equipo ORDER BY codigo";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) equipos.add(mapear(rs));
        }
        return equipos;
    }

    /** Devuelve el equipo con ese codigo o null si no existe. */
    public Equipo consultarPorCodigo(String codigo) throws SQLException {
        String sql = "SELECT codigo, nombre, id_area, consumo_normal_kwh, porcentaje_extra "
                   + "FROM equipo WHERE codigo = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Actualiza nombre y area sin cambiar la clave. Devuelve false si no existe. */
    public boolean actualizar(Equipo equipo) throws SQLException {
        return actualizar(equipo.getCodigo(), equipo.getCodigo(), equipo.getNombre(),
                equipo.getIdArea());
    }

    /**
     * Actualiza nombre, area y, opcionalmente, el codigo. PostgreSQL propaga el nuevo codigo
     * a las claves foraneas con ON UPDATE CASCADE; el gestor solo permite cambiarlo cuando
     * el equipo no tiene historial. Devuelve false si el codigo original no existe.
     */
    public boolean actualizar(String codigoActual, String nuevoCodigo, String nombre, int idArea)
            throws SQLException {
        String sql = "UPDATE equipo SET codigo = ?, nombre = ?, id_area = ? WHERE codigo = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nuevoCodigo);
            ps.setString(2, nombre);
            ps.setInt(3, idArea);
            ps.setString(4, codigoActual);
            return ps.executeUpdate() > 0;
        }
    }

    /** Indica si el equipo tiene ordenes o registros de carbono que impidan cambiar su clave. */
    public boolean tieneDependencias(String codigo) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM orden_mantenimiento WHERE codigo_equipo = ?) "
                   + "OR EXISTS (SELECT 1 FROM registro_carbono WHERE codigo_equipo = ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigo);
            ps.setString(2, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Guarda los parametros de consumo asociados al equipo. No modifica el nombre ni el area
     * y no duplica esos datos en cada registro de carbono. Devuelve false si no existe.
     */
    public boolean actualizarParametrosCarbono(String codigo, BigDecimal consumoNormalKwh,
                                               BigDecimal porcentajeExtra) throws SQLException {
        String sql = "UPDATE equipo SET consumo_normal_kwh = ?, porcentaje_extra = ? WHERE codigo = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setBigDecimal(1, consumoNormalKwh);
            ps.setBigDecimal(2, porcentajeExtra);
            ps.setString(3, codigo);
            return ps.executeUpdate() > 0;
        }
    }

    /** Elimina un equipo. PostgreSQL rechaza (23503) si tiene ordenes asociadas (ON DELETE RESTRICT). */
    public boolean eliminar(String codigo) throws SQLException {
        String sql = "DELETE FROM equipo WHERE codigo = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigo);
            return ps.executeUpdate() > 0;
        }
    }

    private Equipo mapear(ResultSet rs) throws SQLException {
        BigDecimal consumo = rs.getBigDecimal("consumo_normal_kwh");
        BigDecimal porcentaje = rs.getBigDecimal("porcentaje_extra");
        return new Equipo(rs.getString("codigo"), rs.getString("nombre"), rs.getInt("id_area"),
                consumo == null ? BigDecimal.ZERO : consumo,
                porcentaje == null ? BigDecimal.ZERO : porcentaje);
    }
}
