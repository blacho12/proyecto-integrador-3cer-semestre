# SGMI — API REST de Gestión de Mantenimiento Industrial

Migración del sistema de escritorio (Java Swing + JDBC) a una **API REST con Spring Boot 3.5 y Java 21**, preparada para desplegarse en **Render** como *Web Service* con Docker y para conectarse a **PostgreSQL en Aiven**.

La lógica académica se conserva en `modelo/`, `estructuras/` (AVL, cola de prioridad y pila) y los DAO JDBC de `datos/`; ahora la pila se reconstruye desde PostgreSQL y el AVL contiene solo equipos activos. La interfaz Swing y el menú de consola se eliminaron.

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
         │                 AVL y cola: cachés reconstruibles.
         │                 Pila LIFO: persistida en PostgreSQL.
         ▼
┌──────────────────┐
│      datos/      │  DAO con JDBC puro (PreparedStatement)
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
    │   ├── modelo/                 # Area, Equipo, OrdenMantenimiento, Criticidad
    │   ├── estructuras/            # ArbolAVL, ColaPrioridad, PilaAcciones, ...
    │   ├── datos/                  # DAO JDBC, incluida la persistencia de acciones
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
        ├── esquema.sql
        ├── esquema_carbono.sql
        ├── migracion_identidad_equipo_acciones.sql
        └── reiniciar_datos_desarrollo.sql
```

### Qué se eliminó y por qué

- `ui/`: toda la interfaz Swing (paneles, temas, diálogos y gráficas). Ya no se usa.
- `app/`: menú de consola, clase `GestorOrdenes` y programas `Prueba*`. Su lógica se trasladó a `services/`; el nuevo punto de entrada es `SgmiApplication`.
- `src_backup/`: copia antigua del código, duplicada y desactualizada.
- `LEEME.txt`, `salida_pruebas.txt` y `sesion_menu_demo.sh`: documentación/salidas de la versión de escritorio.

### Cómo se usan las estructuras académicas

- **AVL de equipos**: se construye desde PostgreSQL al primer uso y contiene únicamente equipos activos. `GET /api/equipos` devuelve el recorrido inorden (ordenado por código).
- **Cola de prioridad**: contiene solo órdenes `PENDIENTE`. Se carga desde la base y se actualiza al crear, modificar, cerrar, cancelar o deshacer. `GET /api/ordenes/cola` devuelve el orden de atención sin alterar la cola.
- **Pila de acciones**: PostgreSQL guarda el tipo, fecha, snapshot JSONB y estado de deshacer de cada acción. La estructura LIFO en memoria se reconstruye al iniciar; una acción compuesta `BAJA_EQUIPO` incluye el equipo y las órdenes pendientes eliminadas con sus posiciones de atención.

> **Importante (escalado):** AVL y cola son cachés por proceso; la pila y sus snapshots son compartidos en PostgreSQL. Las operaciones de órdenes, bajas y deshacer se serializan con bloqueos de PostgreSQL, y las consultas reconstruyen la caché antes de usarla.

### Reglas de negocio conservadas

- **Criticidad**: `puntaje = 2 × impacto + 2 × riesgoSeguridad + 2` si no hay respaldo (0 si hay respaldo). `ALTA` si el riesgo es 3 o el puntaje ≥ 11; `MEDIA` si ≥ 7; `BAJA` en otro caso.
- **Orden de atención**: primero la mayor criticidad; a igual criticidad, la orden más antigua (menor `consecutivo`).
- **Estados**: `PENDIENTE`, `CERRADA`, `CANCELADA`. Al cerrar o cancelar se asigna `fecha_cierre`; una orden pendiente no tiene fecha de cierre (regla `ck_orden_fechas`).
- **Deshacer**: revierte la última acción (`CREAR`, `MODIFICAR`, `CERRAR`, `CANCELAR` o `BAJA_EQUIPO`) en la misma transacción que la marca como deshecha. La pila conserva el orden LIFO tras reiniciar.
- **Equipos**: `id_equipo` es la PK autogenerada; `codigo` es único e inmutable desde la API y sigue siendo el identificador de negocio/índice AVL. La baja es lógica. Reenviar `POST /api/equipos` con un código inactivo reactiva ese registro y no crea otra identidad.
- **Órdenes al dar de baja**: se eliminan únicamente órdenes `PENDIENTE`, que son las únicas abiertas según la cola actual. `CERRADA` y `CANCELADA` son terminales y se conservan; el snapshot permite restaurar pendientes, identificadores, fechas y prioridad.

---

## 2. Requisitos

- JDK 21
- Maven 3.9+ (opcional: el proyecto incluye Maven Wrapper y puede compilar sin Maven global)
- PostgreSQL con el esquema de `src/main/resources/esquema.sql` aplicado (local o Aiven)
- Para habilitar huella de carbono, aplicar también `src/main/resources/esquema_carbono.sql`
- Para una base existente creada con el esquema anterior, aplicar una vez `src/main/resources/migracion_identidad_equipo_acciones.sql`
- Docker (opcional, para probar la imagen o desplegar en Render)

### Base de datos

El proyecto **no genera scripts para crear usuarios ni ejecuta migraciones al arrancar**. Para una base nueva, aplique el esquema:

```bash
psql "postgresql://USUARIO:CLAVE@HOST:PUERTO/BASE?sslmode=require" \
     -f src/main/resources/esquema.sql
```

Si la base en la nube es Aiven, copie el host, puerto, base y usuario desde la consola de Aiven y agregue `?sslmode=require` a la URL JDBC.

La migración `esquema_carbono.sql` agrega los parámetros energéticos por equipo,
configura el factor inicial (`0.1500 kg CO₂/kWh`) y crea el historial de carbono. Aplíquela
una vez después de `esquema.sql` cuando se habilite carbono:

```bash
psql "postgresql://HOST:PUERTO/BASE?sslmode=require" \
     -f src/main/resources/esquema_carbono.sql
```

Para una base ya creada con el esquema anterior (`equipo.codigo` como PK), respalde la base
y aplique **una sola vez** la migración siguiente antes de desplegar el backend nuevo. Esta
migración cambia las claves foráneas de órdenes y, si ya existe, carbono; crea la tabla de
acciones vacía. No limpia ni recrea el esquema:

```bash
psql "postgresql://HOST:PUERTO/BASE?sslmode=require" \
     -v ON_ERROR_STOP=1 \
     -f src/main/resources/migracion_identidad_equipo_acciones.sql
```

La migración no está diseñada para volver a ejecutarse ni para una base nueva que ya use
`esquema.sql`. Las acciones históricas anteriores vivían solo en memoria y no se pueden
recuperar; la tabla persistente empieza vacía. Inspeccione primero el resultado y respalde
especialmente antes de actualizar una base compartida.

### Reiniciar datos de desarrollo

El reinicio conserva el esquema, elimina las áreas, equipos, órdenes, registros de carbono
si existen y todas las acciones (incluidos snapshots); reinicia las secuencias y restablece
el factor de carbono al valor inicial `0.1500`. **Es destructivo e irreversible. No lo
ejecute contra producción ni sobre una base compartida.** El script falla salvo que la
base esté nombrada con `dev`, `development`, `local` o `test`, no contenga `prod`, se
confirme el entorno y la URL resuelva al nombre esperado.

```bash
APP_ENV=development \
DEV_DATABASE_URL='postgresql://localhost:5432/mantenimiento_dev' \
DEV_DATABASE_NAME=mantenimiento_dev \
CONFIRM_RESET_DEVELOPMENT_DATA=YES \
sh scripts/reiniciar-datos-desarrollo.sh
```

Si la base de aprendizaje/pruebas tiene otro nombre (por ejemplo la `defaultdb` de Aiven),
agregue `RESET_CONFIRM_DATABASE_NAME` con su nombre **exacto**. El script y el SQL vuelven
a comprobar que coincida con `DEV_DATABASE_NAME` y con la base real; además se mantienen
obligatorios `APP_ENV=development`, la confirmación explícita y el rechazo de nombres con
`prod`. No use este mecanismo con una base de producción o compartida:

```bash
export PGPASSWORD='su_clave_real'
APP_ENV=development PGUSER=avnadmin \
DEV_DATABASE_URL='postgresql://HOST:PUERTO/defaultdb?sslmode=require' \
DEV_DATABASE_NAME=defaultdb \
RESET_CONFIRM_DATABASE_NAME=defaultdb \
CONFIRM_RESET_DEVELOPMENT_DATA=YES \
sh scripts/reiniciar-datos-desarrollo.sh
```

El SQL del reinicio también exige la guarda `app.reset_guard=DEVELOPMENT_ONLY` que el
script inyecta; ejecutarlo a mano con `psql` sin esa guarda falla. El backend no realiza
este reinicio automáticamente. La base debe tener aplicado el esquema nuevo y la tabla
`accion_historial`.

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

El backend carga el AVL, la cola de órdenes pendientes y la pila persistente durante el
arranque; PostgreSQL debe estar disponible. Si falla la carga, el inicio falla explícitamente
y debe corregirse la conexión antes de volver a arrancar.

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
   - `CORS_ALLOWED_ORIGINS` → origen del frontend, por ejemplo `https://mi-frontend.onrender.com`; puede agregar más orígenes separados por comas. Para desarrollo local use `http://localhost:5500`.
   - **No defina `PORT`**: Render lo inyecta automáticamente y la aplicación lo toma con `${PORT:8080}`.
5. Antes de desplegar, aplique `migracion_identidad_equipo_acciones.sql` una sola vez si la base ya existía con el esquema anterior. Para una base nueva aplique `esquema.sql`; en ambos casos aplique `esquema_carbono.sql` si habilitará carbono (ver sección 2). No ejecute scripts de reinicio en Render/Aiven.
6. En **Health Check Path** configure `/api/estado`.
7. Despliegue. El `Dockerfile` compila con Maven y JDK 21 y ejecuta el JAR con un JRE 21 sin privilegios de root.

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
| `GET` | `/api/equipos` | — | Equipos activos ordenados por código (inorden del AVL): `id`, `codigo`, `nombre`, `idArea`, `activo` | `200` |
| `GET` | `/api/equipos/{codigo}` | `codigo` en la ruta | Un equipo activo; los inactivos dan `404` | `200`, `404` |
| `POST` | `/api/equipos` | `{"codigo":"...","nombre":"...","idArea":1}` | Equipo creado (`201`) o registro inactivo reactivado (`200`) | `200`, `201`, `400`, `409` |
| `PUT` | `/api/equipos/{codigo}` | `{"nombre":"...","idArea":1}` | Equipo actualizado (el código no cambia) | `200`, `400`, `404`, `409` |
| `DELETE` | `/api/equipos/{codigo}` | — | Baja lógica, elimina solo pendientes y registra una acción compuesta deshacible | `204`, `404`, `409` |

```bash
curl -X DELETE "http://localhost:8080/api/equipos/EQ-001"
```

El registro del equipo y su código único se conservan. Se eliminan físicamente solo
órdenes `PENDIENTE`; las `CERRADA` y `CANCELADA` permanecen en la base y en el historial.
El historial completo de órdenes conserva `codigoEquipo`, incluso si el equipo está inactivo.
Para volver a dar de alta el mismo código, `POST /api/equipos` reactiva la identidad
existente; no se permite insertar un duplicado.

### 5.3 Órdenes — `/api/ordenes`

| Método | Ruta | Cuerpo / Parámetros | Devuelve | Códigos |
|---|---|---|---|---|
| `GET` | `/api/ordenes` | `?estado=PENDIENTE` y/o `?codigoEquipo=EQ-001` (opcionales) | Lista histórica de órdenes | `200`, `400` |
| `GET` | `/api/ordenes/{id}` | `id` en la ruta | Una orden | `200`, `404` |
| `GET` | `/api/ordenes/cola` | — | Órdenes pendientes en orden de atención (la primera es la más prioritaria) | `200` |
| `GET` | `/api/ordenes/historial` | — | Acciones persistidas para deshacer, de la más reciente a la más antigua | `200` |
| `POST` | `/api/ordenes` | Ver ejemplo abajo | Orden creada con `id`, `consecutivo` y `fechaCreacion` de PostgreSQL | `201`, `400`, `404`, `409` |
| `PUT` | `/api/ordenes/{id}` | `{"tipo","descripcion","impacto","riesgoSeguridad","hayRespaldo"}` | Orden modificada (recalcula prioridad) | `200`, `400`, `404`, `409` |
| `POST` | `/api/ordenes/{id}/cerrar` | — | Orden en estado `CERRADA` con `fechaCierre` | `200`, `404`, `409` |
| `POST` | `/api/ordenes/{id}/cancelar` | — | Orden en estado `CANCELADA` con `fechaCierre` | `200`, `404`, `409` |
| `POST` | `/api/ordenes/atender-siguiente` | — | Cierra la orden más prioritaria de la cola | `200`, `404`, `409` |
| `POST` | `/api/ordenes/historial/deshacer` | — | Acción revertida atómicamente; incluye reactivación y restauración de órdenes al deshacer `BAJA_EQUIPO` | `200`, `404`, `409` |

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
  "idAccion": 12,
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

Una baja se representa como una sola acción `BAJA_EQUIPO`; su respuesta incluye
`equipoPrevio` y `ordenesEliminadas`. El snapshot JSONB persistido incluye los IDs,
consecutivos, estado, fechas y posición de prioridad de cada orden pendiente. Los IDs se
restauran explícitamente y las secuencias PostgreSQL se sincronizan en la misma operación.
La acción se marca como deshecha solo si la reactivación y todas las restauraciones
terminan correctamente.

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

### 5.5 Huella de carbono — `/api/carbono`

La estimación replica el cálculo de la versión Swing: `consumo adicional (kWh) =
consumo normal (kWh/h) × horas en mal estado × porcentaje extra / 100`; `emisiones
(kg CO₂) = consumo adicional × factor (kg CO₂/kWh)`. Los resultados se redondean a
cuatro decimales (`HALF_UP`). Los valores negativos y porcentajes superiores a 100 se
rechazan. El factor configurado inicialmente por la migración es el valor definido en la
versión original; se puede cambiar desde la API. Cada registro conserva el factor usado.

| Método | Ruta | Devuelve | Códigos |
|---|---|---|---|
| `GET` | `/api/carbono/resumen` | Total, equipos con emisiones, conteos, agrupaciones por área/equipo e historial persistido | `200` |
| `GET` | `/api/carbono/registros` | Historial completo, más reciente primero | `200` |
| `POST` | `/api/carbono/registros` | Calcula y persiste un registro | `201`, `400`, `404` |
| `GET` | `/api/carbono/configuracion` | Factor de emisión vigente | `200` |
| `PUT` | `/api/carbono/configuracion` | Actualiza el factor de la planta | `200`, `400` |
| `PUT` | `/api/carbono/equipos/{codigo}/parametros` | Actualiza consumo normal y porcentaje extra del equipo | `200`, `400`, `404` |

Ejemplos de cuerpos:

```json
{"codigoEquipo":"EQ-001","horasMalEstado":8}
{"consumoNormalKwh":10.00,"porcentajeExtra":15.00}
{"factorEmision":0.1500}
```

`GET /api/equipos` incluye `consumoNormalKwh` y `porcentajeExtra`. El resumen se calcula
desde los registros guardados, no desde datos de muestra; las agrupaciones vacías se
devuelven como listas vacías y el total como cero.

### 5.6 Estado del servicio — `/api/estado`

| Método | Ruta | Devuelve | Códigos |
|---|---|---|---|
| `GET` | `/api/estado` | `estado`, `baseDatos`, `timestamp` | `200` conectado, `503` sin conexión |

Se usa como **Health Check** en Render.

### 5.7 Códigos de error

| Código | Cuándo ocurre |
|---|---|
| `400 Bad Request` | Cuerpo inválido, campo faltante, tipo/estado no permitido o parámetro con formato incorrecto. |
| `404 Not Found` | El área, equipo u orden no existe, no está pendiente o la ruta no existe. |
| `409 Conflict` | Código de equipo activo duplicado, clave foránea inválida, área asociada o conflicto concurrente al deshacer. |
| `500 Internal Server Error` | Error inesperado de PostgreSQL o del servidor. |
| `503 Service Unavailable` | No hay conexión con PostgreSQL. |

---

## 6. Verificación

```bash
./mvnw clean package
```

Las pruebas unitarias y de contrato HTTP corren con Maven:

```bash
curl http://localhost:8080/api/estado
curl http://localhost:8080/api/areas
curl http://localhost:8080/api/equipos
curl http://localhost:8080/api/ordenes
curl http://localhost:8080/api/ordenes/cola
curl http://localhost:8080/api/estadisticas
curl http://localhost:8080/api/carbono/resumen
```

```bash
./mvnw test
```

La suite incluye el round-trip JSON del snapshot compuesto (equipo, IDs, fechas y posición
de cola), preservación de estado/fecha terminal y el orden determinista de la cola.

Las pruebas de integración se ejecutan contra PostgreSQL real y están deshabilitadas en la
suite normal para no depender de una base local. El script incluido levanta un cluster
temporal aislado en el directorio de temporales, crea la base `sgmi_test`, aplica
`esquema.sql` y `esquema_carbono.sql`, ejecuta las pruebas y detiene el cluster:

```bash
./scripts/probar-integracion.sh
```

También puede apuntar a una base propia de desarrollo/test:

```bash
TEST_DB_URL='jdbc:postgresql://127.0.0.1:55432/sgmi_test' \
TEST_DB_USER=postgres TEST_DB_PASSWORD='' \
./mvnw -Dtest=BajaEquipoIntegracionTest test
```

> **Advertencia:** la prueba recrea el esquema `public` de la base indicada. El propio test
> se niega a ejecutarse si el nombre de la base no contiene `test`, `dev` o `local`, o si
> contiene `prod`. No la ejecute contra Aiven ni contra una base compartida.

La prueba de integración cubre: baja sin órdenes, baja con pendientes conservando
`CERRADA`/`CANCELADA`, restauración de IDs, fechas y prioridad, deshacer tras reconstruir
las estructuras como en un reinicio, reactivación sin duplicar el código, reconstrucción del
AVL solo con activos, rollback ante una falla simulada con un trigger y dos procesos que
intentan deshacer la misma acción sin restaurar dos veces.

---

## 7. Notas de seguridad

- No escriba credenciales en `application.properties`, en el código ni en `Dockerfile`.
- Mantenga `.env` fuera del control de versiones (ya está en `.gitignore`).
- En Render, cargue `DB_URL`, `DB_USER`, `DB_PASSWORD` y `CORS_ALLOWED_ORIGINS` en **Environment**.
- Use siempre `?sslmode=require` cuando la base esté fuera de la red local.
