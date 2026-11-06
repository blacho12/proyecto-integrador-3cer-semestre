# SGMI — Gestión de órdenes de mantenimiento industrial

## a. Datos del proyecto

**Título:** Gestión de órdenes de mantenimiento en una planta industrial según criticidad del equipo.  
**ODS:** 9. Industria, innovación e infraestructura.

**Descripción:** El prototipo administra áreas, equipos y órdenes de mantenimiento de una planta industrial. Calcula la criticidad de cada orden según el impacto en la producción, el riesgo para la seguridad y la disponibilidad de equipos de respaldo. Organiza las órdenes pendientes mediante una cola de prioridad, indexa los equipos con un árbol AVL y registra acciones para permitir deshacer operaciones. La información se almacena en PostgreSQL y se consulta desde una interfaz web conectada a una API REST.

El proyecto contribuye a las metas **9.1**, al favorecer la disponibilidad de la infraestructura productiva; **9.4**, al priorizar intervenciones y reducir paradas y desperdicios; y **9.5**, al incorporar una herramienta tecnológica con reglas explícitas para gestionar el mantenimiento.

| Integrante | Rol y responsabilidad |
|---|---|
| Sebastián Díaz Juris | Coordinador: planifica tareas y controla el cronograma. |
| Bladimir González Martínez | Desarrollador de estructuras: implementa nodos, estructuras y operaciones. |
| Gabriel Ángel Arrieta Duarte | Desarrollador de aplicación: construye la interfaz, carga de datos y pruebas. |
| Salomón Josué Bray Barrios | Investigador y documentador: consulta fuentes, redacta el informe y prepara el póster. |

## b. Tecnologías

- **Backend:** Java 21 y Spring Boot 3.5; API REST.
- **Frontend:** HTML, CSS, Bootstrap 5 y JavaScript.
- **Base de datos:** PostgreSQL, conexión mediante JDBC / `JdbcTemplate`.
- **Driver JDBC:** PostgreSQL, declarado como dependencia en `pom.xml`.
- **Construcción:** Maven Wrapper incluido (`mvnw` y `mvnw.cmd`).

## Ingresar directamente desde la URL: https://proyecto-integrador-frontend-oejl.onrender.

## c. Instalación y ejecución (si quiere probarlo de manera local con su propia base de datos)

### Requisitos

JDK 21, PostgreSQL, `psql` y un navegador actualizado. Maven no necesita instalación global porque el repositorio incluye Maven Wrapper. El driver JDBC se descarga como dependencia Maven.

### 1. Crear la base de datos

Cree una base, por ejemplo `DB-INTEGRADOR-3S`, y desde la raíz del repositorio ejecute:

```bash
psql -U postgres -d DB-INTEGRADOR-3S -f src/main/resources/esquema.sql
```

Para habilitar el módulo de huella de carbono, ejecute también:

```bash
psql -U postgres -d DB-INTEGRADOR-3S -f src/main/resources/esquema_carbono.sql
```

`migracion_identidad_equipo_acciones.sql` se utiliza únicamente para actualizar bases existentes con el esquema anterior; no se ejecuta sobre una base nueva. `reiniciar_datos_desarrollo.sql` es destructivo y solo debe utilizarse en bases de desarrollo.

### 2. Configurar la conexión

Copie la plantilla:

```bash
cp .env.ejemplo .env
```

Edite `.env` con los datos de su PostgreSQL:

```properties
DB_URL=jdbc:postgresql://localhost:5432/DB-INTEGRADOR-3S
DB_USER=postgres
DB_PASSWORD=SU_CLAVE
```

Para una base remota, configure el host, puerto y nombre correspondientes y agregue `?sslmode=require` a la URL JDBC. No suba el archivo `.env` al repositorio ni comparta sus credenciales.

### 3. Ejecutar el backend

Linux/macOS:

```bash
./mvnw clean package
java -jar target/gestor-mantenimiento-1.0.0.jar
```

Windows:

```bat
mvnw.cmd clean package
java -jar target\gestor-mantenimiento-1.0.0.jar
```

También puede utilizar `./mvnw spring-boot:run`. La API queda disponible en `http://localhost:8080`. Verifique el servicio y la conexión a la base de datos con:

```bash
curl http://localhost:8080/api/estado
```

La respuesta debe indicar `estado: ok` y `baseDatos: conectada`.

### 4. Ejecutar el frontend

Desde otra terminal, en la raíz del repositorio:

```bash
python3 -m http.server 5500 --directory frontend
```

Abra `http://localhost:5500`. Para usar el backend local, visite:

```text
http://localhost:5500/?api=http://localhost:8080/api
```

Si el navegador bloquea las solicitudes, configure CORS en el backend para permitir el origen `http://localhost:5500`. No abra `index.html` directamente mediante `file://`.

## d. Estructuras implementadas

| Estructura | Clase | Entidad o tabla gestionada | Función |
|---|---|---|---|
| Árbol AVL | `ArbolAVL` y `NodoAVL` | `Equipo` / `equipo` | Indexa equipos por código; inserta, busca, modifica, elimina y recorre en orden. |
| Cola de prioridad (heap binario) | `ColaPrioridad` | `OrdenMantenimiento` / `orden_mantenimiento` | Prioriza órdenes pendientes por criticidad y antigüedad. |
| Pila enlazada (LIFO) | `PilaAcciones`, `NodoPila` y `Accion` | Historial / `accion_historial` | Registra acciones y permite deshacer la última operación. |

El AVL y la cola se reconstruyen desde PostgreSQL y se mantienen en memoria. El historial de acciones y sus snapshots se persisten en PostgreSQL para reconstruir la pila después de reiniciar el backend.

## e. Estado actual

El repositorio contiene operaciones para gestionar áreas, equipos y órdenes; calcular criticidad; consultar y atender la cola; consultar el historial y deshacer acciones; obtener estadísticas; y gestionar huella de carbono (requiere `esquema_carbono.sql`). Incluye pruebas unitarias y pruebas de integración con PostgreSQL.

**Verificación pendiente antes de la Feria Innova:** ejecutar `./mvnw test` y `./scripts/probar-integracion.sh`, además de probar manualmente la creación, modificación, cierre, cancelación y deshacer de órdenes. Después, reiniciar el backend y comprobar que los datos permanecen en PostgreSQL. La presencia de pruebas en el repositorio no demuestra que ya se hayan ejecutado en el equipo de demostración.

**Limitaciones y precauciones conocidas:**
- La prueba de integración recrea el esquema de la base indicada. Utilice únicamente una base aislada de desarrollo/pruebas; nunca producción ni una base compartida.
- El módulo de carbono requiere aplicar `esquema_carbono.sql`.
- El frontend depende de la disponibilidad de la API y de que CORS permita su origen.
- No se certifica aquí el funcionamiento en producción ni la ejecución satisfactoria de las pruebas; esos resultados deben registrarse después de la verificación real.
