package modelo;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consolidado calculado exclusivamente con los registros persistidos en PostgreSQL:
 * total de la planta, emisiones por area, emisiones por equipo y numero de equipos
 * que generaron emisiones adicionales mayores que cero.
 */
public class ResumenCarbono {
    private final List<RegistroCarbono> registros;
    private final BigDecimal totalEmisiones;
    private final Map<String, BigDecimal> emisionesPorArea;
    private final Map<String, BigDecimal> emisionesPorEquipo;
    private final int equiposConEmisiones;

    public ResumenCarbono(List<RegistroCarbono> registros, BigDecimal totalEmisiones,
                          Map<String, BigDecimal> emisionesPorArea,
                          Map<String, BigDecimal> emisionesPorEquipo,
                          int equiposConEmisiones) {
        this.registros = List.copyOf(registros);
        this.totalEmisiones = totalEmisiones;
        this.emisionesPorArea = Collections.unmodifiableMap(new LinkedHashMap<>(emisionesPorArea));
        this.emisionesPorEquipo = Collections.unmodifiableMap(new LinkedHashMap<>(emisionesPorEquipo));
        this.equiposConEmisiones = equiposConEmisiones;
    }

    public List<RegistroCarbono> getRegistros() { return registros; }
    public BigDecimal getTotalEmisiones() { return totalEmisiones; }
    public Map<String, BigDecimal> getEmisionesPorArea() { return emisionesPorArea; }
    public Map<String, BigDecimal> getEmisionesPorEquipo() { return emisionesPorEquipo; }
    public int getEquiposConEmisiones() { return equiposConEmisiones; }
}
