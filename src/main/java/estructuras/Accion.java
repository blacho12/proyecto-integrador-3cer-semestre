package estructuras;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/** Accion LIFO con la informacion necesaria para persistir y revertir su snapshot. */
public class Accion {
    public enum Tipo { CREAR, MODIFICAR, CANCELAR, CERRAR, BAJA_EQUIPO }

    private long idAccion;
    private final OrdenMantenimiento estadoPrevio;
    private final Equipo equipoPrevio;
    private final List<OrdenEnCola> ordenesEliminadas;
    private final Tipo tipo;
    private final LocalDateTime fecha;

    public Accion(Tipo tipo, OrdenMantenimiento estadoPrevio) {
        this(0, tipo, LocalDateTime.now().withNano(0), estadoPrevio, null, List.of());
    }

    public Accion(Tipo tipo, Equipo equipoPrevio, List<OrdenEnCola> ordenesEliminadas) {
        this(0, tipo, LocalDateTime.now().withNano(0), null, equipoPrevio, ordenesEliminadas);
    }

    private Accion(long idAccion, Tipo tipo, LocalDateTime fecha, OrdenMantenimiento estadoPrevio,
                   Equipo equipoPrevio, List<OrdenEnCola> ordenesEliminadas) {
        this.idAccion = idAccion;
        this.tipo = tipo;
        this.fecha = fecha;
        this.estadoPrevio = estadoPrevio;
        this.equipoPrevio = equipoPrevio;
        this.ordenesEliminadas = List.copyOf(ordenesEliminadas);
    }

    public static Accion desdePersistencia(long idAccion, Tipo tipo, LocalDateTime fecha, Snapshot snapshot) {
        OrdenMantenimiento previa = snapshot.estadoPrevio() == null
                ? null : snapshot.estadoPrevio().aOrden();
        Equipo equipo = snapshot.equipoPrevio() == null
                ? null : snapshot.equipoPrevio().aEquipo();
        List<OrdenEnCola> eliminadas = snapshot.ordenesEliminadas().stream()
                .map(orden -> new OrdenEnCola(orden.aOrden(), orden.posicionCola()))
                .toList();
        return new Accion(idAccion, tipo, fecha, previa, equipo, eliminadas);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                estadoPrevio == null ? null : OrdenSnapshot.de(estadoPrevio, -1),
                equipoPrevio == null ? null : EquipoSnapshot.de(equipoPrevio),
                ordenesEliminadas.stream()
                        .map(orden -> OrdenSnapshot.de(orden.orden(), orden.posicion()))
                        .toList());
    }

    public long getIdAccion() { return idAccion; }
    public void setIdAccion(long idAccion) { this.idAccion = idAccion; }
    public Tipo getTipo() { return tipo; }
    public OrdenMantenimiento getEstadoPrevio() { return estadoPrevio; }
    public Equipo getEquipoPrevio() { return equipoPrevio; }
    public List<OrdenEnCola> getOrdenesEliminadas() { return ordenesEliminadas; }
    public LocalDateTime getFecha() { return fecha; }
    public long getIdOrden() { return estadoPrevio != null ? estadoPrevio.getId() : -1L; }

    public record OrdenEnCola(OrdenMantenimiento orden, int posicion) { }

    public record Snapshot(
            OrdenSnapshot estadoPrevio,
            EquipoSnapshot equipoPrevio,
            List<OrdenSnapshot> ordenesEliminadas) {
        public Snapshot {
            ordenesEliminadas = ordenesEliminadas == null ? List.of() : List.copyOf(ordenesEliminadas);
        }
    }

    public record EquipoSnapshot(
            long id, String codigo, String nombre, int idArea, BigDecimal consumoNormalKwh,
            BigDecimal porcentajeExtra, boolean activo) {
        static EquipoSnapshot de(Equipo equipo) {
            return new EquipoSnapshot(equipo.getId(), equipo.getCodigo(), equipo.getNombre(),
                    equipo.getIdArea(), equipo.getConsumoNormalKwh(), equipo.getPorcentajeExtra(),
                    equipo.isActivo());
        }

        Equipo aEquipo() {
            return new Equipo(id, codigo, nombre, idArea, consumoNormalKwh, porcentajeExtra, activo);
        }
    }

    public record OrdenSnapshot(
            long id, long consecutivo, long idEquipo, String codigoEquipo, String tipo,
            String descripcion, int impacto, int riesgoSeguridad, boolean hayRespaldo,
            Estado estado, LocalDateTime fechaCreacion, LocalDateTime fechaCierre, int posicionCola) {
        static OrdenSnapshot de(OrdenMantenimiento orden, int posicionCola) {
            return new OrdenSnapshot(orden.getId(), orden.getConsecutivo(), orden.getIdEquipo(),
                    orden.getCodigoEquipo(), orden.getTipo(), orden.getDescripcion(), orden.getImpacto(),
                    orden.getRiesgoSeguridad(), orden.isHayRespaldo(), orden.getEstado(),
                    orden.getFechaCreacion(), orden.getFechaCierre(), posicionCola);
        }

        OrdenMantenimiento aOrden() {
            return new OrdenMantenimiento(id, consecutivo, idEquipo, codigoEquipo, tipo, descripcion,
                    impacto, riesgoSeguridad, hayRespaldo, estado, fechaCreacion, fechaCierre);
        }
    }
}
