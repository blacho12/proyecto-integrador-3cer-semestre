package com.grupo7.sgmi.services;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.grupo7.sgmi.dto.EstadisticasDTO;

import datos.AreaDAO;
import datos.EquipoDAO;
import datos.OrdenDAO;
import modelo.Area;
import modelo.Equipo;
import modelo.OrdenMantenimiento;

/**
 * Indicadores calculados exclusivamente con las ordenes, equipos y areas persistidos.
 * El area de cada orden se resuelve desde PostgreSQL, de modo que una orden historica
 * sigue asociada a su area aunque el equipo ya este dado de baja. El AVL solo aporta
 * los contadores de equipos activos.
 */
@Service
public class EstadisticasService {

    private final EjecutorBD ejecutor;
    private final EstadoEnMemoria estado;

    public EstadisticasService(EjecutorBD ejecutor, EstadoEnMemoria estado) {
        this.ejecutor = ejecutor;
        this.estado = estado;
    }

    public EstadisticasDTO calcular() {
        List<OrdenMantenimiento> ordenes = ejecutor.enLectura(
                conexion -> new OrdenDAO(conexion).consultarTodas());
        List<Area> areas = ejecutor.enLectura(
                conexion -> new AreaDAO(conexion).consultarTodas());
        List<Equipo> equipos = ejecutor.enLectura(
                conexion -> new EquipoDAO(conexion).consultarTodosIncluyendoInactivos());

        Map<Integer, String> nombrePorArea = new HashMap<>();
        for (Area area : areas) {
            nombrePorArea.put(area.getIdArea(), area.getNombre());
        }
        Map<String, Integer> areaPorCodigoEquipo = new HashMap<>();
        for (Equipo equipo : equipos) {
            areaPorCodigoEquipo.put(equipo.getCodigo(), equipo.getIdArea());
        }

        Map<String, Long> porEstado = new LinkedHashMap<>();
        porEstado.put("PENDIENTE", 0L);
        porEstado.put("CERRADA", 0L);
        porEstado.put("CANCELADA", 0L);

        Map<String, Long> porCriticidad = new LinkedHashMap<>();
        porCriticidad.put("ALTA", 0L);
        porCriticidad.put("MEDIA", 0L);
        porCriticidad.put("BAJA", 0L);

        Map<String, Long> porTipo = new LinkedHashMap<>();
        porTipo.put("PREVENTIVO", 0L);
        porTipo.put("CORRECTIVO", 0L);

        Map<String, Long> porArea = new LinkedHashMap<>();

        synchronized (estado) {
            estado.recargar();
            for (OrdenMantenimiento orden : ordenes) {
                porEstado.merge(orden.getEstado().name(), 1L, Long::sum);
                porCriticidad.merge(orden.getCriticidad().name(), 1L, Long::sum);
                porTipo.merge(orden.getTipo(), 1L, Long::sum);

                Integer idArea = areaPorCodigoEquipo.get(orden.getCodigoEquipo());
                String area;
                if (idArea == null) {
                    area = "Sin area (equipo no registrado)";
                } else {
                    area = nombrePorArea.getOrDefault(idArea, "Area " + idArea);
                }
                porArea.merge(area, 1L, Long::sum);
            }

            return new EstadisticasDTO(
                    ordenes.size(),
                    porEstado.get("PENDIENTE"),
                    porEstado.get("CERRADA"),
                    porEstado.get("CANCELADA"),
                    estado.getEquipos().tamano(),
                    estado.getEquipos().altura(),
                    estado.getOrdenesPendientes().tamano(),
                    estado.getHistorial().tamano(),
                    porEstado,
                    porCriticidad,
                    porTipo,
                    porArea);
        }
    }
}
