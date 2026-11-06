package datos;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import estructuras.Accion;

/** Persistencia de la pila de deshacer en PostgreSQL. */
public class AccionDAO {
    private static final long BLOQUEO_PILA = 7_177_413_447L;

    private final Connection conexion;
    private final ObjectMapper objectMapper;

    public AccionDAO(Connection conexion, ObjectMapper objectMapper) {
        this.conexion = conexion;
        this.objectMapper = objectMapper;
    }

    public void bloquearPila() throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            ps.setLong(1, BLOQUEO_PILA);
            ps.executeQuery().close();
        }
    }

    public Accion insertar(Accion accion) throws SQLException {
        String sql = "INSERT INTO accion_historial (tipo, fecha, snapshot) VALUES (?, ?, ?::jsonb) "
                   + "RETURNING id_accion";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, accion.getTipo().name());
            ps.setObject(2, accion.getFecha());
            ps.setString(3, serializar(accion.snapshot()));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("El INSERT de la accion no devolvio su id.");
                accion.setIdAccion(rs.getLong("id_accion"));
                return accion;
            }
        }
    }

    public Accion consultarTopeParaActualizar() throws SQLException {
        String sql = "SELECT id_accion, tipo, fecha, snapshot::text AS snapshot "
                   + "FROM accion_historial WHERE NOT deshecha "
                   + "ORDER BY id_accion DESC LIMIT 1 FOR UPDATE";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? mapear(rs) : null;
        }
    }

    public List<Accion> consultarActivas() throws SQLException {
        String sql = "SELECT id_accion, tipo, fecha, snapshot::text AS snapshot "
                   + "FROM accion_historial WHERE NOT deshecha ORDER BY id_accion";
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Accion> acciones = new ArrayList<>();
            while (rs.next()) acciones.add(mapear(rs));
            return acciones;
        }
    }

    public boolean marcarDeshecha(long idAccion) throws SQLException {
        String sql = "UPDATE accion_historial SET deshecha = TRUE "
                   + "WHERE id_accion = ? AND NOT deshecha";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, idAccion);
            return ps.executeUpdate() == 1;
        }
    }

    private Accion mapear(ResultSet rs) throws SQLException {
        try {
            Accion.Snapshot snapshot = objectMapper.readValue(rs.getString("snapshot"), Accion.Snapshot.class);
            return Accion.desdePersistencia(rs.getLong("id_accion"),
                    Accion.Tipo.valueOf(rs.getString("tipo")),
                    rs.getObject("fecha", LocalDateTime.class), snapshot);
        } catch (IOException | IllegalArgumentException e) {
            throw new SQLException("No se pudo leer el snapshot persistido de la accion "
                    + rs.getLong("id_accion") + ".", "XX000", e);
        }
    }

    private String serializar(Accion.Snapshot snapshot) throws SQLException {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (IOException e) {
            throw new SQLException("No se pudo serializar el snapshot de la accion.", "XX000", e);
        }
    }
}
