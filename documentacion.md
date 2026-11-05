# SGMI — API REST de Gestión de Mantenimiento Industrial

Migración del sistema de escritorio (Java Swing + JDBC) a una **API REST con Spring Boot 3.5 y Java 21**, preparada para desplegarse en **Render** como *Web Service* con Docker y para conectarse a **PostgreSQL en Aiven**.

La lógica académica se conserva: las clases de `modelo/`, las estructuras propias de `estructuras/` (AVL, cola de prioridad y pila) y los DAO JDBC de `datos/` **no fueron modificados**. La interfaz Swing y el menú de consola se eliminaron.

---

## 1. Arquitectura

```
Petición HTTP (JSON)
        │
        ▼
┌──────────────────┐
│   controllers/   │  Expone los endpoints REST y valida el cuerpo con @Valid
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│    services/     │  Reglas de negocio, transacciones y sincronización
│                  │  de las estructuras en memoria
└────────┬─────────┘
         │
         ├──────────────► estructuras/ (ArbolAVL, ColaPrioridad, PilaAcciones)
         │                 Requisito académico. Viven en memoria.
         ▼
┌──────────────────┐
│      datos/      │  DAO con JDBC puro (PreparedStatement) — sin cambios
└────────┬─────────┘
         │
         ▼
   PostgreSQL (Aiven / local)
```

### Capas nuevas

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Arranque | `com.grupo7.sgmi` | `SgmiApplication`, habilita Spring Boot y el escaneo de componentes. |
| Controladores | `com.grupo7.sgmi.controllers` | Endpoints REST, códigos HTTP y cuerpos JSON. |
| Servicios | `com.grupo7.sgmi.services` | Casos de uso, transacciones, cola de prioridad, pila de deshacer y estadísticas. |
| DTO | `com.grupo7.sgmi.dto` | Cuerpos de entrada validados y respuestas propias (acciones, errores, estadísticas). |
| Excepciones | `com.grupo7.sgmi.excepciones` | Excepciones de dominio y manejador global que devuelve errores JSON uniformes. |

### Estructura del proyecto

```
.
├── Dockerfile
├── documentacion.md
├── .dockerignore
├── .env.ejemplo
├── pom.xml
└── src/main/
    ├── java/
    │   ├── modelo/                 # SIN CAMBIOS (Area, Equipo, OrdenMantenimiento, Criticidad)
    │   ├── estructuras/            # SIN CAMBIOS (ArbolAVL, ColaPrioridad, PilaAcciones, ...)
    │   ├── datos/                  # SIN CAMBIOS (AreaDAO, EquipoDAO, OrdenDAO, ConexionBD, Configuracion)
    │   └── com/grupo7/sgmi/
    │       ├── SgmiApplication.java
    │       ├── controllers/
    │       │   ├── AreaController.java
    │       │   ├── EquipoController.java
    │       │   ├── OrdenController.java
    │       │   ├── EstadisticasController.java
    │       │   └── EstadoController.java
    │       ├── services/
    │       │   ├── AreaService.java
    │       │   ├── EquipoService.java
    │       │   ├── OrdenService.java
    │       │   ├── EstadisticasService.java
    │       │   ├── EstadoEnMemoria.java
    │       │   └── EjecutorBD.java
    │       ├── dto/
    │       └── excepciones/
    └── resources/
        ├── application.properties
        └── esquema.sql
```

### Qué se eliminó y por qué

- `ui/`: toda la interfaz Swing (paneles, temas, diálogos y gráficas). Ya no se usa.
- `app/`: menú de consola, clase `GestorOrdenes` y programas `Prueba*`. Su lógica se trasladó a `services/`; el nuevo punto de entrada es `SgmiApplication`.
- `src_backup/`: copia antigua del código, duplicada y desactualizada.
- `LEEME.txt`, `salida_pruebas.txt` y `sesion_menu_demo.sh`: documentación/salidas de la versión de escritorio.

### Cómo se usan las estructuras académicas

- **AVL de equipos**: se carga desde PostgreSQL la primera vez que se usa la API y se mantiene sincronizado en cada alta, modificación o borrado de equipos. `GET /api/equipos` devuelve el recorrido inorden (ordenado por código).
- **Cola de prioridad**: contiene solo órdenes `PENDIENTE`. Se carga desde la base y se actualiza al crear, modificar, cerrar, cancelar o deshacer. `GET /api/ordenes/cola` devuelve el orden de atención sin alterar la cola.
- **Pila de acciones**: guarda las operaciones para deshacer (LIFO). Es **temporal**: vive en la memoria del proceso y se pierde al reiniciar. PostgreSQL sigue siendo la fuente de verdad.

> **Importante (escalado):** la cola, el AVL y la pila son estado en memoria del proceso. Con una sola instancia en Render todo es consistente. Si el servicio se escala a varias instancias, cada una tendrá su propia cola/pila; los datos persistidos siguen siendo los mismos en PostgreSQL.

### Reglas de negocio conservadas

- **Criticidad**: `puntaje = 2 × impacto + 2 × riesgoSeguridad + 2` si no hay respaldo (0 si hay respaldo). `ALTA` si el riesgo es 3 o el puntaje ≥ 11; `MEDIA` si ≥ 7; `BAJA` en otro caso.
- **Orden de atención**: primero la mayor criticidad; a igual criticidad, la orden más antigua (menor `consecutivo`).
- **Estados**: `PENDIENTE`, `CERRADA`, `CANCELADA`. Al cerrar o cancelar se asigna `fecha_cierre`; una orden pendiente no tiene fecha de cierre (regla `ck_orden_fechas`).
- **Deshacer**: revierte la última acción (`CREAR`, `MODIFICAR`, `CERRAR` o `CANCELAR`) y solo retira la acción de la pila si PostgreSQL confirmó el cambio.

---

## 2. Requisitos

- JDK 21
- Maven 3.9+ (opcional: el proyecto incluye Maven Wrapper y puede compilar sin Maven global)
- PostgreSQL con el esquema de `src/main/resources/esquema.sql` aplicado (local o Aiven)
- Docker (opcional, para probar la imagen o desplegar en Render)

### Base de datos

El proyecto **no genera scripts para crear usuarios**. Solo necesita una base PostgreSQL existente y aplicar una vez el esquema:

```bash
psql "postgresql://USUARIO:CLAVE@HOST:PUERTO/BASE?sslmode=require" \
     -f src/main/resources/esquema.sql
```

Si la base en la nube es Aiven, copie el host, puerto, base y usuario desde la consola de Aiven y agregue `?sslmode=require` a la URL JDBC.

---

## 3. Ejecución local

### 3.1 Crear el `.env`

```bash
cp .env.ejemplo .env
```

Edite `.env` con sus credenciales:

```properties
DB_URL=jdbc:postgresql://pg-xxxxxxxx-mixto.e.aivencloud.com:23333/defaultdb?sslmode=require
DB_USER=avnadmin
DB_PASSWORD=su_clave_real
# DB_POOL_SIZE=5
```

`application.properties` carga ese archivo automáticamente con:

```properties
spring.config.import=optional:file:./.env[.properties]
```

- El archivo se interpreta con el formato `CLAVE=valor` (estilo `.properties`).
- **No use comillas** alrededor de los valores.
- Si una contraseña contiene una barra invertida (`\`), escríbala doble (`\\`).
- Las variables de entorno reales del sistema tienen prioridad sobre el `.env`.
- El `.env` está en `.gitignore` y en `.dockerignore`: **nunca se sube al repositorio ni se copia a la imagen**.

### 3.2 Arrancar

El repositorio incluye **Maven Wrapper**, así que no es necesario instalar Maven globalmente.

En Linux o macOS:

```bash
./mvnw clean package
java -jar target/gestor-mantenimiento-1.0.0.jar
```

En Windows:

```bat
mvnw.cmd clean package
java -jar target\gestor-mantenimiento-1.0.0.jar
```

También puede arrancar directamente en modo desarrollo:

```bash
./mvnw spring-boot:run
```

Si ya tiene Maven instalado, los comandos `mvn` equivalentes siguen funcionando.

La API queda en `http://localhost:8080`. Verifique la conexión con:

```bash
curl http://localhost:8080/api/estado
```

Respuesta esperada:

```json
{"estado":"ok","baseDatos":"conectada","timestamp":"2026-11-04T22:11:12"}
```

Si PostgreSQL no responde, el endpoint devuelve `503` y la aplicación sigue arrancando (el pool reintenta en la siguiente petición).

### 3.3 Docker local

```bash
docker build -t sgmi-api .
docker run --rm -p 8080:8080 --env-file .env sgmi-api
```

El contenedor escucha en el puerto indicado por `PORT` o, si no existe, en `8080`.

---

## 4. Despliegue en Render (Web Service)

1. Cree un **New → Web Service** y conecte el repositorio de GitHub.
2. Seleccione la rama `migracion-springboot`.
3. En **Language/Runtime** elija **Docker**. Render detecta el `Dockerfile` de la raíz.
4. En **Environment** agregue:
   - `DB_URL` → URL JDBC de Aiven terminada en `?sslmode=require`
   - `DB_USER` → usuario de la base
   - `DB_PASSWORD` → contraseña de la base
   - `DB_POOL_SIZE` (opcional) → por ejemplo `5`
   - **No defina `PORT`**: Render lo inyecta automáticamente y la aplicación lo toma con `${PORT:8080}`.
5. En **Health Check Path** configure `/api/estado`.
6. Despliegue. El `Dockerfile` compila con Maven y JDK 21 y ejecuta el JAR con un JRE 21 sin privilegios de root.

La imagen no incluye el archivo `.env`; las credenciales llegan como variables de entorno del servicio, tal como exige la configuración segura.

---

## 5. Endpoints REST

Base URL local: `http://localhost:8080`.
Todas las respuestas son JSON (`Content-Type: application/json`). Los errores usan este formato:

```json
{
  "timestamp": "2026-11-04T22:11:32",
  "status": 404,
  "error": "Not Found",
  "mensaje": "No existe el area con id 999999.",
  "ruta": "/api/areas/999999"
}
```

### 5.1 Áreas — `/api/areas`

| Método | Ruta | Cuerpo / Parámetros | Devuelve | Códigos |
|---|---|---|---|---|
| `GET` | `/api/areas` | — | Lista de áreas: `idArea`, `nombre`, `descripcion` | `200` |
| `GET` | `/api/areas/{idArea}` | `idArea` en la ruta | Un área | `200`, `404` |
| `POST` | `/api/areas` | `{"nombre":"...","descripcion":"..."}` | Área creada con su `idArea` generado | `201`, `400`, `409` |
| `PUT` | `/api/areas/{idArea}` | `{"nombre":"...","descripcion":"..."}` | Área actualizada | `200`, `400`, `404`, `409` |
| `DELETE` | `/api/areas/{idArea}` | — | Sin cuerpo | `204`, `404`, `409` (tiene equipos asociados) |

### 5.2 Equipos — `/api/equipos`

| Método | Ruta | Cuerpo / Parámetros | Devuelve | Códigos |
|---|---|---|---|---|
| `GET` | `/api/equipos` | — | Equipos ordenados por código (inorden del AVL): `codigo`, `nombre`, `idArea` | `200` |
| `GET` | `/api/equipos/{codigo}` | `codigo` en la ruta | Un equipo | `200`, `404` |
| `POST` | `/api/equipos` | `{"codigo":"...","nombre":"...","idArea":1}` | Equipo creado | `201`, `400`, `409` |
| `PUT` | `/api/equipos/{codigo}` | `{"nombre":"...","idArea":1}` | Equipo actualizado (el código no cambia) | `200`, `400`, `404`, `409` |
| `DELETE` | `/api/equipos/{codigo}` | `?eliminarOrdenes=false` (por defecto) | Sin cuerpo | `204`, `404`, `409` |

Para borrar un equipo con órdenes asociadas hay que confirmar el borrado en cascada:

```bash
curl -X DELETE "http://localhost:8080/api/equipos/EQ-001?eliminarOrdenes=true"
```

Sin `eliminarOrdenes=true`, PostgreSQL rechaza el borrado por la clave foránea `ON DELETE RESTRICT` y la API devuelve `409`.

### 5.3 Órdenes — `/api/ordenes`

| Método | Ruta | Cuerpo / Parámetros | Devuelve | Códigos |
|---|---|---|---|---|
| `GET` | `/api/ordenes` | `?estado=PENDIENTE` y/o `?codigoEquipo=EQ-001` (opcionales) | Lista histórica de órdenes | `200`, `400` |
| `GET` | `/api/ordenes/{id}` | `id` en la ruta | Una orden | `200`, `404` |
| `GET` | `/api/ordenes/cola` | — | Órdenes pendientes en orden de atención (la primera es la más prioritaria) | `200` |
| `GET` | `/api/ordenes/historial` | — | Acciones para deshacer, de la más reciente a la más antigua | `200` |
| `POST` | `/api/ordenes` | Ver ejemplo abajo | Orden creada con `id`, `consecutivo` y `fechaCreacion` de PostgreSQL | `201`, `400`, `404`, `409` |
| `PUT` | `/api/ordenes/{id}` | `{"tipo","descripcion","impacto","riesgoSeguridad","hayRespaldo"}` | Orden modificada (recalcula prioridad) | `200`, `400`, `404`, `409` |
| `POST` | `/api/ordenes/{id}/cerrar` | — | Orden en estado `CERRADA` con `fechaCierre` | `200`, `404`, `409` |
| `POST` | `/api/ordenes/{id}/cancelar` | — | Orden en estado `CANCELADA` con `fechaCierre` | `200`, `404`, `409` |
| `POST` | `/api/ordenes/atender-siguiente` | — | Cierra la orden más prioritaria de la cola | `200`, `404`, `409` |
| `POST` | `/api/ordenes/historial/deshacer` | — | Acción revertida | `200`, `404`, `409` |

Ejemplo de creación:

```bash
curl -X POST http://localhost:8080/api/ordenes \
  -H "Content-Type: application/json" \
  -d '{
    "codigoEquipo": "EQ-001",
    "tipo": "CORRECTIVO",
    "descripcion": "Fuga de vapor en la caldera",
    "impacto": 3,
    "riesgoSeguridad": 3,
    "hayRespaldo": false
  }'
```

Respuesta (`201 Created`):

```json
{
  "id": 10,
  "consecutivo": 10,
  "codigoEquipo": "EQ-001",
  "tipo": "CORRECTIVO",
  "descripcion": "Fuga de vapor en la caldera",
  "impacto": 3,
  "riesgoSeguridad": 3,
  "hayRespaldo": false,
  "estado": "PENDIENTE",
  "fechaCreacion": "2026-11-04T22:15:00",
  "criticidad": "ALTA"
}
```

Reglas de validación del cuerpo:

- `tipo`: `PREVENTIVO` o `CORRECTIVO` (se acepta minúsculas y se normaliza).
- `descripcion`: obligatoria.
- `impacto` y `riesgoSeguridad`: enteros de 1 a 3.
- `hayRespaldo`: booleano obligatorio.
- `codigoEquipo` (solo al crear): debe existir en el catálogo.

Formato de una acción del historial:

```json
{
  "tipo": "CERRAR",
  "idOrden": 10,
  "fecha": "2026-11-04T22:16:10",
  "estadoPrevio": {
    "id": 10,
    "consecutivo": 10,
    "codigoEquipo": "EQ-001",
    "tipo": "CORRECTIVO",
    "descripcion": "Fuga de vapor en la caldera",
    "impacto": 3,
    "riesgoSeguridad": 3,
    "hayRespaldo": false,
    "estado": "PENDIENTE",
    "fechaCreacion": "2026-11-04T22:15:00",
    "criticidad": "ALTA"
  }
}
```

### 5.4 Estadísticas — `/api/estadisticas`

| Método | Ruta | Devuelve | Códigos |
|---|---|---|---|
| `GET` | `/api/estadisticas` | Totales y distribuciones calculadas con datos reales | `200` |

Ejemplo:

```json
{
  "totalOrdenes": 3,
  "pendientes": 3,
  "cerradas": 0,
  "canceladas": 0,
  "equiposEnIndice": 1,
  "alturaArbolEquipos": 1,
  "ordenesEnCola": 3,
  "accionesPorDeshacer": 0,
  "ordenesPorEstado": {"PENDIENTE": 3, "CERRADA": 0, "CANCELADA": 0},
  "ordenesPorCriticidad": {"ALTA": 1, "MEDIA": 1, "BAJA": 1},
  "ordenesPorTipo": {"PREVENTIVO": 0, "CORRECTIVO": 3},
  "ordenesPorArea": {"salud": 3}
}
```

### 5.5 Estado del servicio — `/api/estado`

| Método | Ruta | Devuelve | Códigos |
|---|---|---|---|
| `GET` | `/api/estado` | `estado`, `baseDatos`, `timestamp` | `200` conectado, `503` sin conexión |

Se usa como **Health Check** en Render.

### 5.6 Códigos de error

| Código | Cuándo ocurre |
|---|---|
| `400 Bad Request` | Cuerpo inválido, campo faltante, tipo/estado no permitido o parámetro con formato incorrecto. |
| `404 Not Found` | El área, equipo u orden no existe, no está pendiente o la ruta no existe. |
| `409 Conflict` | Clave duplicada, clave foránea inválida, área con equipos, equipo con órdenes o una orden que ya no está pendiente. |
| `500 Internal Server Error` | Error inesperado de PostgreSQL o del servidor. |
| `503 Service Unavailable` | No hay conexión con PostgreSQL. |

---

## 6. Verificación

```bash
./mvnw clean package
```

El proyecto no incluye pruebas automatizadas con JUnit; la verificación principal es la compilación y las pruebas manuales contra la base de datos:

```bash
curl http://localhost:8080/api/estado
curl http://localhost:8080/api/areas
curl http://localhost:8080/api/equipos
curl http://localhost:8080/api/ordenes
curl http://localhost:8080/api/ordenes/cola
curl http://localhost:8080/api/estadisticas
```

---

## 7. Notas de seguridad

- No escriba credenciales en `application.properties`, en el código ni en `Dockerfile`.
- Mantenga `.env` fuera del control de versiones (ya está en `.gitignore`).
- En Render, cargue `DB_URL`, `DB_USER` y `DB_PASSWORD` en **Environment**.
- Use siempre `?sslmode=require` cuando la base esté fuera de la red local.
