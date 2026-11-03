package datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/** Acceso a datos de la tabla orden_mantenimiento. Todas las consultas usan PreparedStatement. */
public class OrdenDAO {
    private final Connection conexion;

    private static final String COLUMNAS =
            "id_orden, consecutivo, codigo_equipo, tipo, descripcion, impacto_produccion, "
            + "riesgo_seguridad, hay_respaldo, estado, fecha_creacion, fecha_cierre";

    public OrdenDAO(Connection conexion) { this.conexion = conexion; }

    /**
     * Inserta una orden y devuelve un OBJETO NUEVO con id_orden, consecutivo y fecha_creacion
     * generados por PostgreSQL (los contadores nunca los calcula Java).
     */
    public OrdenMantenimiento insertar(OrdenMantenimiento orden) throws SQLException {
        String sql = "INSERT INTO orden_mantenimiento "
                   + "(codigo_equipo, tipo, descripcion, impacto_produccion, riesgo_seguridad, hay_respaldo) "
                   + "VALUES (?, ?, ?, ?, ?, ?) "
                   + "RETURNING id_orden, consecutivo, fecha_creacion";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, orden.getCodigoEquipo());
            ps.setString(2, orden.getTipo());
            ps.setString(3, orden.getDescripcion());
            ps.setInt(4, orden.getImpacto());
            ps.setInt(5, orden.getRiesgoSeguridad());
            ps.setBoolean(6, orden.isHayRespaldo());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("El INSERT de la orden no devolvio los campos generados");
                return new OrdenMantenimiento(
                        rs.getLong("id_orden"), rs.getLong("consecutivo"),
                        orden.getCodigoEquipo(), orden.getTipo(), orden.getDescripcion(),
                        orden.getImpacto(), orden.getRiesgoSeguridad(), orden.isHayRespaldo(),
                        Estado.PENDIENTE, rs.getObject("fecha_creacion", LocalDateTime.class), null);
            }
        }
    }

    public OrdenMantenimiento consultarPorId(long id) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM orden_mantenimiento WHERE id_orden = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public List<OrdenMantenimiento> consultarTodas() throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM orden_mantenimiento ORDER BY consecutivo";
        return consultarVarias(sql);
    }

    /** Consulta las ordenes de un estado concreto (PENDIENTE, CERRADA o CANCELADA). */
    public List<OrdenMantenimiento> consultarPorEstado(Estado estado) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM orden_mantenimiento WHERE estado = ? ORDER BY consecutivo";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<OrdenMantenimiento> ordenes = new ArrayList<>();
                while (rs.next()) ordenes.add(mapear(rs));
                return ordenes;
            }
        }
    }

    /** Ordenes de un equipo (historico completo), de la mas antigua a la mas reciente. */
    public List<OrdenMantenimiento> consultarPorEquipo(String codigoEquipo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM orden_mantenimiento WHERE codigo_equipo = ? ORDER BY consecutivo";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, codigoEquipo);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrdenMantenimiento> ordenes = new ArrayList<>();
                while (rs.next()) ordenes.add(mapear(rs));
                return ordenes;
            }
        }
    }

    /**
     * Actualiza los campos editables de la orden (tipo, descripcion y criterios de criticidad).
     * El id, el consecutivo, el equipo y las fechas no se tocan aqui; el estado tiene
     * operaciones propias. Devuelve false si el id no existe.
     */
    public boolean actualizar(OrdenMantenimiento orden) throws SQLException {
        String sql = "UPDATE orden_mantenimiento "
                   + "SET tipo = ?, descripcion = ?, impacto_produccion = ?, riesgo_seguridad = ?, hay_respaldo = ? "
                   + "WHERE id_orden = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, orden.getTipo());
            ps.setString(2, orden.getDescripcion());
            ps.setInt(3, orden.getImpacto());
            ps.setInt(4, orden.getRiesgoSeguridad());
            ps.setBoolean(5, orden.isHayRespaldo());
            ps.setLong(6, orden.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cambia el estado de una orden PENDIENTE a CERRADA o CANCELADA y le asigna fecha de cierre.
     * La clausula AND estado = 'PENDIENTE' evita cerrar/cancelar dos veces la misma orden.
     * Devuelve false si la orden no existia o ya no esta pendiente.
     */
    public boolean cambiarEstado(long id, Estado nuevoEstado, LocalDateTime fechaCierre) throws SQLException {
        if (nuevoEstado == Estado.PENDIENTE) throw new SQLException("Usar restaurarPendiente para volver a PENDIENTE");
        String sql = "UPDATE orden_mantenimiento SET estado = ?, fecha_cierre = ? "
                   + "WHERE id_orden = ? AND estado = 'PENDIENTE'";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado.name());
            ps.setObject(2, fechaCierre);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Revierte una orden CERRADA o CANCELADA al estado PENDIENTE con fecha_cierre NULL.
     * Devuelve false si la orden no existia o ya estaba pendiente (evita dobles deshacer).
     */
    public boolean restaurarPendiente(long id) throws SQLException {
        String sql = "UPDATE orden_mantenimiento SET estado = 'PENDIENTE', fecha_cierre = NULL "
                   + "WHERE id_orden = ? AND estado IN ('CERRADA', 'CANCELADA')";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Elimina fisicamente una orden (solo para revertir una accion CREAR al deshacer). */
    public boolean eliminar(long id) throws SQLException {
        String sql = "DELETE FROM orden_mantenimiento WHERE id_orden = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private List<OrdenMantenimiento> consultarVarias(String sql) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<OrdenMantenimiento> ordenes = new ArrayList<>();
            while (rs.next()) ordenes.add(mapear(rs));
            return ordenes;
        }
    }

    /** Construye el objeto Java completo desde una fila de la tabla. */
    private OrdenMantenimiento mapear(ResultSet rs) throws SQLException {
        return new OrdenMantenimiento(
                rs.getLong("id_orden"),
                rs.getLong("consecutivo"),
                rs.getString("codigo_equipo"),
                rs.getString("tipo"),
                rs.getString("descripcion"),
                rs.getInt("impacto_produccion"),
                rs.getInt("riesgo_seguridad"),
                rs.getBoolean("hay_respaldo"),
                Estado.valueOf(rs.getString("estado")),
                rs.getObject("fecha_creacion", LocalDateTime.class),
                rs.getObject("fecha_cierre", LocalDateTime.class));
    }
}
