package datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import modelo.Area;

/** Acceso a datos de la tabla area. Todas las consultas usan PreparedStatement. */
public class AreaDAO {
    private final Connection conexion;

    public AreaDAO(Connection conexion) { this.conexion = conexion; }

    /** Inserta un area y devuelve el objeto con su id_area generado por PostgreSQL. */
    public Area insertar(String nombre, String descripcion) throws SQLException {
        String sql = "INSERT INTO area (nombre, descripcion) VALUES (?, ?) RETURNING id_area";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, descripcion);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("El INSERT de area no devolvio el id generado");
                return new Area(rs.getInt(1), nombre, descripcion);
            }
        }
    }

    public List<Area> consultarTodas() throws SQLException {
        List<Area> areas = new ArrayList<>();
        String sql = "SELECT id_area, nombre, descripcion FROM area ORDER BY id_area";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) areas.add(mapear(rs));
        }
        return areas;
    }

    /** Devuelve el area con ese id o null si no existe. */
    public Area consultarPorId(int idArea) throws SQLException {
        String sql = "SELECT id_area, nombre, descripcion FROM area WHERE id_area = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idArea);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Actualiza nombre y descripcion; devuelve false si el id no existe. */
    public boolean actualizar(Area area) throws SQLException {
        String sql = "UPDATE area SET nombre = ?, descripcion = ? WHERE id_area = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, area.getNombre());
            ps.setString(2, area.getDescripcion());
            ps.setInt(3, area.getIdArea());
            return ps.executeUpdate() > 0;
        }
    }

    /** Comprueba si ya existe un area con ese nombre (sin distinguir mayusculas). */
    public boolean existeNombre(String nombre, int excluirIdArea) throws SQLException {
        String sql = "SELECT 1 FROM area WHERE LOWER(nombre) = LOWER(?) AND id_area <> ? LIMIT 1";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setInt(2, excluirIdArea);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Numero de equipos que referencian el area (para bloquear el borrado en cascada). */
    public int contarEquipos(int idArea) throws SQLException {
        String sql = "SELECT COUNT(*) FROM equipo WHERE id_area = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idArea);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Cantidad de equipos por area, para mostrarla en la tabla de gestion de areas. */
    public Map<Integer, Integer> contarEquiposPorArea() throws SQLException {
        Map<Integer, Integer> conteo = new LinkedHashMap<>();
        String sql = "SELECT id_area, COUNT(*) AS total FROM equipo GROUP BY id_area ORDER BY id_area";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) conteo.put(rs.getInt("id_area"), rs.getInt("total"));
        }
        return conteo;
    }

    /**
     * Elimina un area. PostgreSQL rechaza (SQLState 23503) si tiene equipos asociados
     * (ON DELETE RESTRICT); ese error llega como SQLException al llamador.
     */
    public boolean eliminar(int idArea) throws SQLException {
        String sql = "DELETE FROM area WHERE id_area = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idArea);
            return ps.executeUpdate() > 0;
        }
    }

    private Area mapear(ResultSet rs) throws SQLException {
        return new Area(rs.getInt("id_area"), rs.getString("nombre"), rs.getString("descripcion"));
    }
}
