package datos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Punto unico de acceso a PostgreSQL por JDBC.
 *
 * Las credenciales se resuelven primero desde las variables de entorno DB_URL, DB_USER y
 * DB_PASSWORD y, si no estan definidas, desde un archivo .env local (por ejemplo
 * src/main/.env). Nunca se escriben dentro del codigo.
 */
public final class ConexionBD {
    private ConexionBD() { /* utilitaria: no se instancia */ }

    /** Crea una conexion nueva a PostgreSQL usando el entorno o el archivo .env. */
    public static Connection getConnection() throws SQLException {
        String url = ConfiguracionEntorno.obtener("DB_URL");
        String usuario = ConfiguracionEntorno.obtener("DB_USER");
        String password = ConfiguracionEntorno.obtener("DB_PASSWORD");

        if (url.isBlank() || usuario.isBlank() || password.isBlank()) {
            throw new SQLException(
                    "Faltan las credenciales DB_URL, DB_USER y DB_PASSWORD. "
                    + "Definalas como variables de entorno o en un archivo .env "
                    + "(por ejemplo src/main/.env o .env en la raiz del proyecto). "
                    + "Tambien puede indicar otra ruta con SGMI_ENV_FILE=/ruta/al/.env.");
        }

        try {
            return DriverManager.getConnection(url, usuario, password);
        } catch (SQLException e) {
            throw new SQLException(
                    "No se pudo conectar a PostgreSQL con el usuario '" + usuario
                    + "' (credenciales leidas de " + ConfiguracionEntorno.origen() + "): "
                    + mensajeAmigable(e), e);
        }
    }

    /** Traduce los SQLState mas frecuentes a un mensaje comprensible para el usuario. */
    public static String mensajeAmigable(SQLException e) {
        String estado = e.getSQLState();
        if (estado == null) return e.getMessage();
        switch (estado) {
            case "08001": case "08006": case "08S01":
                return "No hay conexion con el servidor de PostgreSQL.";
            case "23503":
                return "Clave foranea invalida: el area, equipo u orden relacionado no existe, "
                     + "o el equipo tiene ordenes o registros de carbono asociados.";
            case "23505":
                return "Ya existe un registro con esa clave (codigo o nombre duplicado).";
            case "23514":
                return "Datos invalidos segun las reglas de la tabla (tipo, valores 1 a 3, estado o fechas).";
            case "22001":
                return "Un texto excede la longitud permitida por la base de datos.";
            case "22P02":
                return "No se pudo interpretar un valor numerico o booleano.";
            case "42P01":
                return "Falta una tabla en la base de datos. Aplique esquema_carbono_areas.sql.";
            case "42703":
                return "Falta una columna en la base de datos. Aplique esquema_carbono_areas.sql.";
            case "42000":
                return "El usuario de la base de datos no tiene permisos para esta operacion.";
            default:
                return e.getMessage();
        }
    }
}
