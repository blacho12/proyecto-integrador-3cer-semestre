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
    public void insertar(Equipo equipo) throws SQLException {
        String sql = "INSERT INTO equipo (codigo, nombre, id_area) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, equipo.getCodigo());
            ps.setString(2, equipo.getNombre());
            ps.setInt(3, equipo.getIdArea());
            ps.executeUpdate();
        }
    }

    public List<Equipo> consultarTodos() throws SQLException {
        List<Equipo> equipos = new ArrayList<>();
        String sql = "SELECT codigo, nombre, id_area FROM equipo ORDER BY codigo";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) equipos.add(mapear(rs));
        }
        return equipos;
    }

    /** Devuelve el equipo con ese codigo o null si no existe. */
    public Equipo consultarPorCodigo(String codigo) throws SQLException {
        String sql = "SELECT codigo, nombre, id_area FROM equipo WHERE codigo = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Actualiza nombre y area; el codigo es la clave y no cambia. Devuelve false si no existe. */
    public boolean actualizar(Equipo equipo) throws SQLException {
        String sql = "UPDATE equipo SET nombre = ?, id_area = ? WHERE codigo = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, equipo.getNombre());
            ps.setInt(2, equipo.getIdArea());
            ps.setString(3, equipo.getCodigo());
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
        return new Equipo(rs.getString("codigo"), rs.getString("nombre"), rs.getInt("id_area"));
    }
}
