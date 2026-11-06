package estructuras;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;
import org.junit.jupiter.api.Test;

class AccionPersistenciaTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializaYReconstruyeElSnapshotCompuestoDeBaja() throws Exception {
        LocalDateTime creada = LocalDateTime.of(2026, 11, 5, 10, 15, 30);
        Equipo equipo = new Equipo(17, "EQ-17", "Bomba", 3,
                new BigDecimal("12.50"), new BigDecimal("8.00"), true);
        OrdenMantenimiento orden = new OrdenMantenimiento(81, 93, 17, "EQ-17",
                "CORRECTIVO", "Fuga", 3, 2, false, Estado.PENDIENTE, creada, null);
        Accion original = new Accion(Accion.Tipo.BAJA_EQUIPO, equipo,
                List.of(new Accion.OrdenEnCola(orden, 2)));

        String json = objectMapper.writeValueAsString(original.snapshot());
        Accion.Snapshot leido = objectMapper.readValue(json, Accion.Snapshot.class);
        Accion reconstruida = Accion.desdePersistencia(44, Accion.Tipo.BAJA_EQUIPO, creada, leido);

        assertEquals(44, reconstruida.getIdAccion());
        assertEquals("EQ-17", reconstruida.getEquipoPrevio().getCodigo());
        assertEquals(17, reconstruida.getEquipoPrevio().getId());
        assertEquals(81, reconstruida.getOrdenesEliminadas().getFirst().orden().getId());
        assertEquals(creada, reconstruida.getOrdenesEliminadas().getFirst().orden().getFechaCreacion());
        assertEquals(2, reconstruida.getOrdenesEliminadas().getFirst().posicion());
    }

    @Test
    void snapshotConservaEstadoTerminalYFechaDeCierre() throws Exception {
        LocalDateTime creada = LocalDateTime.of(2026, 11, 1, 8, 0);
        LocalDateTime cierre = LocalDateTime.of(2026, 11, 2, 9, 0);
        OrdenMantenimiento cerrada = new OrdenMantenimiento(82, 94, 17, "EQ-17",
                "PREVENTIVO", "Inspección", 1, 1, true,
                Estado.CANCELADA, creada, cierre);
        Accion original = new Accion(Accion.Tipo.CANCELAR, cerrada);

        Accion.Snapshot leido = objectMapper.readValue(
                objectMapper.writeValueAsString(original.snapshot()), Accion.Snapshot.class);
        Accion reconstruida = Accion.desdePersistencia(45, Accion.Tipo.CANCELAR, creada, leido);

        assertEquals(Estado.CANCELADA, reconstruida.getEstadoPrevio().getEstado());
        assertEquals(cierre, reconstruida.getEstadoPrevio().getFechaCierre());
    }

    @Test
    void colaReconstruidaMantieneElOrdenDeAtencionDeterminista() {
        ColaPrioridad cola = new ColaPrioridad();
        OrdenMantenimiento antigua = new OrdenMantenimiento(
                1, 1, 17, "EQ-17", "PREVENTIVO", "A", 2, 2, true,
                Estado.PENDIENTE, LocalDateTime.now(), null);
        OrdenMantenimiento masCritica = new OrdenMantenimiento(
                2, 2, 18, "EQ-18", "CORRECTIVO", "B", 3, 3, false,
                Estado.PENDIENTE, LocalDateTime.now(), null);

        cola.encolar(antigua);
        cola.encolar(masCritica);

        assertEquals(2, cola.recorridoPorPrioridad()[0].getId());
        assertEquals(1, cola.recorridoPorPrioridad()[1].getId());
    }
}
