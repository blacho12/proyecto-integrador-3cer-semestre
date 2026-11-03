package datos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Punto unico de acceso a PostgreSQL por JDBC.
 * Las credenciales se toman de las variables de entorno DB_URL, DB_USER y DB_PASSWORD;
 * nunca se escriben dentro del codigo.
 */
public final class ConexionBD {
    private ConexionBD() { /* utilitaria: no se instancia */ }

    /** Crea una conexion nueva a PostgreSQL a partir de las variables de entorno. */
    public static Connection getConnection() throws SQLException {
        String url = System.getenv("DB_URL");
        String usuario = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (url == null || usuario == null || password == null) {
            throw new SQLException(
                    "Faltan las variables de entorno DB_URL, DB_USER y DB_PASSWORD. "
                    + "Ejemplo: DB_URL=jdbc:postgresql://localhost:5432/mantenimiento "
                    + "DB_USER=postgres DB_PASSWORD=****");
        }

        try {
            return DriverManager.getConnection(url, usuario, password);
        } catch (SQLException e) {
            throw new SQLException(
                    "No se pudo conectar a PostgreSQL con DB_URL=" + url
                    + " y DB_USER=" + usuario + ": " + mensajeAmigable(e), e);
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
                return "Clave foranea invalida: el area, equipo u orden relacionado no existe o tiene ordenes asociadas.";
            case "23505":
                return "Ya existe un registro con esa clave (codigo o nombre duplicado).";
            case "23514":
                return "Datos invalidos segun las reglas de la tabla (tipo, valores 1 a 3, estado o fechas).";
            case "22001":
                return "Un texto excede la longitud permitida por la base de datos.";
            case "22P02":
                return "No se pudo interpretar un valor numerico o booleano.";
            case "42000":
                return "El usuario de la base de datos no tiene permisos para esta operacion.";
            default:
                return e.getMessage();
        }
    }
}
