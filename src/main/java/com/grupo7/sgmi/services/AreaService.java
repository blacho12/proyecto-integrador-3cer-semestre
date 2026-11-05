package com.grupo7.sgmi.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.grupo7.sgmi.dto.AreaRequest;
import com.grupo7.sgmi.excepciones.RecursoNoEncontradoException;

import datos.AreaDAO;
import modelo.Area;

/** Reglas de negocio de las areas de la planta (tabla area). */
@Service
public class AreaService {

    private final EjecutorBD ejecutor;

    public AreaService(EjecutorBD ejecutor) {
        this.ejecutor = ejecutor;
    }

    public List<Area> listar() {
        return ejecutor.enLectura(conexion -> new AreaDAO(conexion).consultarTodas());
    }

    public Area obtener(int idArea) {
        Area area = ejecutor.enLectura(conexion -> new AreaDAO(conexion).consultarPorId(idArea));
        if (area == null) {
            throw noEncontrada(idArea);
        }
        return area;
    }

    public Area crear(AreaRequest peticion) {
        String nombre = peticion.nombre().trim();
        String descripcion = normalizar(peticion.descripcion());
        return ejecutor.enTransaccion(conexion -> new AreaDAO(conexion).insertar(nombre, descripcion));
    }

    public Area actualizar(int idArea, AreaRequest peticion) {
        String nombre = peticion.nombre().trim();
        String descripcion = normalizar(peticion.descripcion());
        return ejecutor.enTransaccion(conexion -> {
            AreaDAO dao = new AreaDAO(conexion);
            Area area = dao.consultarPorId(idArea);
            if (area == null) {
                throw noEncontrada(idArea);
            }
            area.setNombre(nombre);
            area.setDescripcion(descripcion);
            if (!dao.actualizar(area)) {
                throw noEncontrada(idArea);
            }
            return area;
        });
    }

    public void eliminar(int idArea) {
        ejecutor.enTransaccion(conexion -> {
            AreaDAO dao = new AreaDAO(conexion);
            if (dao.consultarPorId(idArea) == null) {
                throw noEncontrada(idArea);
            }
            if (!dao.eliminar(idArea)) {
                throw noEncontrada(idArea);
            }
            return null;
        });
    }

    private RecursoNoEncontradoException noEncontrada(int idArea) {
        return new RecursoNoEncontradoException("No existe el area con id " + idArea + ".");
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
