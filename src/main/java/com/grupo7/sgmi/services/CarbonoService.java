package com.grupo7.sgmi.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.grupo7.sgmi.dto.CarbonoConfiguracionDTO;
import com.grupo7.sgmi.dto.CarbonoEmisionesAreaDTO;
import com.grupo7.sgmi.dto.CarbonoEmisionesEquipoDTO;
import com.grupo7.sgmi.dto.CarbonoFactorRequest;
import com.grupo7.sgmi.dto.CarbonoParametrosEquipoRequest;
import com.grupo7.sgmi.dto.CarbonoRegistroDTO;
import com.grupo7.sgmi.dto.CarbonoRegistroRequest;
import com.grupo7.sgmi.dto.CarbonoResumenDTO;
import com.grupo7.sgmi.excepciones.RecursoNoEncontradoException;

import datos.CarbonoDAO;
import datos.EquipoDAO;
import modelo.Equipo;
import modelo.RegistroCarbono;

/** Reglas de negocio del modulo de emisiones adicionales. */
@Service
public class CarbonoService {

    private final EjecutorBD ejecutor;

    public CarbonoService(EjecutorBD ejecutor) {
        this.ejecutor = ejecutor;
    }

    public CarbonoResumenDTO resumen() {
        return ejecutor.enLectura(conexion -> {
            CarbonoDAO dao = new CarbonoDAO(conexion);
            List<RegistroCarbono> registros = dao.consultarTodos();
            Map<String, AcumuladoArea> areas = new TreeMap<>();
            Map<String, AcumuladoEquipo> equipos = new TreeMap<>();
            BigDecimal total = BigDecimal.ZERO.setScale(CalculadoraCarbono.ESCALA);

            for (RegistroCarbono registro : registros) {
                BigDecimal emisiones = registro.emisionesKgCo2();
                total = total.add(emisiones);
                AcumuladoArea area = areas.computeIfAbsent(registro.nombreArea(), AcumuladoArea::new);
                area.emisiones = area.emisiones.add(emisiones);
                AcumuladoEquipo equipo = equipos.computeIfAbsent(registro.codigoEquipo(),
                        codigo -> new AcumuladoEquipo(codigo, registro.nombreEquipo(), registro.nombreArea()));
                equipo.emisiones = equipo.emisiones.add(emisiones);
            }

            List<CarbonoEmisionesAreaDTO> emisionesPorArea = areas.values().stream()
                    .map(area -> new CarbonoEmisionesAreaDTO(area.nombre, area.emisiones))
                    .toList();
            List<CarbonoEmisionesEquipoDTO> emisionesPorEquipo = equipos.values().stream()
                    .map(equipo -> new CarbonoEmisionesEquipoDTO(
                            equipo.codigo, equipo.nombre, equipo.area, equipo.emisiones))
                    .toList();
            int equiposConEmisiones = (int) equipos.values().stream()
                    .filter(equipo -> equipo.emisiones.signum() > 0)
                    .count();

            return new CarbonoResumenDTO(total, equiposConEmisiones, registros.size(),
                    dao.consultarFactorEmision(), emisionesPorArea, emisionesPorEquipo,
                    registros.stream().map(this::dto).toList());
        });
    }

    public List<CarbonoRegistroDTO> registros() {
        return ejecutor.enLectura(conexion -> new CarbonoDAO(conexion).consultarTodos()
                .stream().map(this::dto).toList());
    }

    public CarbonoConfiguracionDTO consultarConfiguracion() {
        return ejecutor.enLectura(conexion ->
                new CarbonoConfiguracionDTO(new CarbonoDAO(conexion).consultarFactorEmision()));
    }

    public CarbonoConfiguracionDTO actualizarFactor(CarbonoFactorRequest peticion) {
        return ejecutor.enTransaccion(conexion -> {
            new CarbonoDAO(conexion).actualizarFactorEmision(peticion.factorEmision());
            return new CarbonoConfiguracionDTO(peticion.factorEmision());
        });
    }

    public Equipo actualizarParametros(String codigo, CarbonoParametrosEquipoRequest peticion) {
        return ejecutor.enTransaccion(conexion -> {
            EquipoDAO equipos = new EquipoDAO(conexion);
            Equipo equipo = equipos.consultarPorCodigo(codigo);
            if (equipo == null || !equipo.isActivo()) {
                throw new RecursoNoEncontradoException("No existe el equipo con codigo '" + codigo + "'.");
            }
            if (!equipos.actualizarParametrosCarbono(
                    codigo, peticion.consumoNormalKwh(), peticion.porcentajeExtra())) {
                throw new RecursoNoEncontradoException("No existe el equipo con codigo '" + codigo + "'.");
            }
            equipo.setConsumoNormalKwh(peticion.consumoNormalKwh());
            equipo.setPorcentajeExtra(peticion.porcentajeExtra());
            return equipo;
        });
    }

    public CarbonoRegistroDTO registrar(CarbonoRegistroRequest peticion) {
        return ejecutor.enTransaccion(conexion -> {
            Equipo equipo = new EquipoDAO(conexion)
                    .consultarActivoPorCodigoParaActualizar(peticion.codigoEquipo());
            if (equipo == null) {
                throw new RecursoNoEncontradoException(
                        "No existe el equipo con codigo '" + peticion.codigoEquipo() + "'.");
            }
            CarbonoDAO carbono = new CarbonoDAO(conexion);
            BigDecimal factor = carbono.consultarFactorEmision();
            CalculadoraCarbono.Resultado resultado = CalculadoraCarbono.calcular(
                    equipo.getConsumoNormalKwh(), peticion.horasMalEstado(),
                    equipo.getPorcentajeExtra(), factor);
            return dto(carbono.insertar(equipo.getId(), peticion.codigoEquipo(), peticion.horasMalEstado(),
                    resultado.consumoAdicionalKwh(), resultado.emisionesKgCo2(), factor));
        });
    }

    private CarbonoRegistroDTO dto(RegistroCarbono registro) {
        return new CarbonoRegistroDTO(registro.idRegistro(), registro.codigoEquipo(),
                registro.nombreEquipo(), registro.nombreArea(), registro.horasMalEstado(),
                registro.consumoAdicionalKwh(), registro.emisionesKgCo2(),
                registro.factorEmision(), registro.fechaRegistro());
    }

    private static final class AcumuladoArea {
        private final String nombre;
        private BigDecimal emisiones = BigDecimal.ZERO.setScale(CalculadoraCarbono.ESCALA);

        private AcumuladoArea(String nombre) {
            this.nombre = nombre;
        }
    }

    private static final class AcumuladoEquipo {
        private final String codigo;
        private final String nombre;
        private final String area;
        private BigDecimal emisiones = BigDecimal.ZERO.setScale(CalculadoraCarbono.ESCALA);

        private AcumuladoEquipo(String codigo, String nombre, String area) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.area = area;
        }
    }
}
