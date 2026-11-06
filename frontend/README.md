# SGMI · Frontend web

Frontend estático del **Sistema de Gestión de Mantenimiento Industrial**, construido con
HTML5, CSS3, Bootstrap 5 y JavaScript puro. Consume la API REST de Spring Boot mediante
`fetch()` y no necesita compilación ni empaquetador.

## 1. Ejecutar

Desde la raíz del repositorio:

```bash
python3 -m http.server 5500 --directory frontend
```

Abra <http://localhost:5500>.

También puede abrir la carpeta `frontend/` desde cualquier servidor estático (Live Server,
Netlify, GitHub Pages, Vercel, etc.). No abra `index.html` con `file://`: los módulos ES y
`fetch()` necesitan un servidor HTTP.

## 2. URL de la API

La constante está en `js/config.js`:

```js
export const API_URL = 'https://proyecto-integrador-3cer-semestre.onrender.com/api';
```

Para probar otro backend sin editar el archivo:

```text
http://localhost:5500/?api=http://localhost:8080/api
```

## 3. CORS

El frontend se sirve desde un origen distinto al backend. La rama incluye
`src/main/java/com/grupo7/sgmi/config/WebConfig.java` para permitir las peticiones del
navegador. En Render, después de desplegar este cambio, la API aceptará peticiones desde
cualquier origen:

```text
CORS_ALLOWED_ORIGINS=*
```

Para restringirlo, use una lista separada por comas, por ejemplo:

```text
CORS_ALLOWED_ORIGINS=https://mi-frontend.onrender.com,http://localhost:5500
```

La API no usa cookies ni credenciales, por eso el valor `*` es seguro para este proyecto.

## 4. Estructura

```text
frontend/
├── index.html              Shell de la SPA, navegación, topbar, toasts y modales
├── css/styles.css          Tema oscuro, layout responsive y componentes
├── js/
│   ├── config.js           API_URL y tiempo máximo de espera
│   ├── api.js              Cliente fetch y métodos de todos los endpoints
│   ├── utils.js            Fechas, escape HTML, criticidad y plantillas
│   ├── ui.js               Toasts, conexión, modales y confirmaciones
│   ├── app.js              Router por hash, navegación y refresco global
│   └── views/
│       ├── dashboard.js
│       ├── ordenes.js
│       ├── cola.js
│       ├── equipos.js
│       ├── areas.js
│       ├── historial.js
│       ├── estadisticas.js
│       └── carbono.js
└── README.md
```

Cada vista exporta `mount(root)` y devuelve `{ refresh, destroy }`; el router se encarga
de destruir la vista anterior y cerrar modales al navegar.

## 5. Paridad con la interfaz Swing

| Panel Swing | Vista web | Operaciones |
|---|---|---|
| Panel principal | `#/dashboard` | Indicadores, próxima orden y estado de cola/AVL/pila |
| Órdenes de mantenimiento | `#/ordenes` | Listar, filtrar, crear, modificar, cerrar, cancelar y ver detalle |
| Cola de prioridad | `#/cola` | Ver tope y recorrido, atender la más prioritaria y cancelar |
| Equipos e índice AVL | `#/equipos` | Listar activos, buscar, crear/reactivar, editar y dar de baja con deshacer |
| Gestión de áreas | `#/areas` | CRUD con conteo de equipos asociados |
| Historial de acciones | `#/historial` | Ver la pila LIFO y deshacer la última acción |
| Indicadores y estadísticas | `#/estadisticas` | Tarjetas y distribuciones reales de la API |
| Huella de carbono | `#/carbono` | Parámetros de equipo, factor de planta, cálculo e historial de emisiones persistidos |

## 6. Endpoints usados

```text
GET    /api/estado
GET    /api/areas
POST   /api/areas
PUT    /api/areas/{idArea}
DELETE /api/areas/{idArea}

GET    /api/equipos
POST   /api/equipos
PUT    /api/equipos/{codigo}
DELETE /api/equipos/{codigo}

GET    /api/ordenes
GET    /api/ordenes/cola
GET    /api/ordenes/historial
POST   /api/ordenes
PUT    /api/ordenes/{id}
POST   /api/ordenes/{id}/cerrar
POST   /api/ordenes/{id}/cancelar
POST   /api/ordenes/atender-siguiente
POST   /api/ordenes/historial/deshacer

GET    /api/estadisticas

GET    /api/carbono/resumen
GET    /api/carbono/registros
POST   /api/carbono/registros
GET    /api/carbono/configuracion
PUT    /api/carbono/configuracion
PUT    /api/carbono/equipos/{codigo}/parametros
```

El resumen agrega el historial persistido. Para una base antigua, aplique primero
`src/main/resources/migracion_identidad_equipo_acciones.sql`; para una base nueva use
`esquema.sql`. Para usar carbono, aplique `src/main/resources/esquema_carbono.sql` a la
misma base PostgreSQL del backend. La API
calcula consumo adicional como `consumo normal × horas en mal estado × porcentaje extra /
100` y emisiones como `consumo adicional × factor de emisión`; conserva el factor usado
en cada registro y rechaza valores negativos o porcentajes mayores que 100.

## 7. Decisiones de negocio conservadas

- **Baja lógica de equipo:** el código se conserva único y el registro se marca inactivo.
  Solo las órdenes `PENDIENTE` se eliminan; las cerradas y canceladas permanecen en el
  historial. La acción compuesta puede deshacerse, incluso después de reiniciar el backend.
- **Identidad del equipo:** `id_equipo` es la clave primaria; el código único e inmutable
  sigue identificando al equipo en la API, el historial y el índice AVL. `POST` con un
  código inactivo reactiva el mismo registro; los inactivos no aparecen como opciones de
  nuevas órdenes.
- **Áreas con equipos:** no se eliminan desde la interfaz ni desde el backend.
- **Criticidad:** se calcula en el cliente solo para la vista previa del formulario y se
  muestra la que devuelve el backend. Fórmula:
  `2 × impacto + 2 × riesgo + 2 si no hay respaldo`.
- **Cola e historial:** el orden y las acciones son responsabilidad del backend; el
  frontend no reordena ni inventa estados.

## 8. Limitaciones conocidas

- **CORS en producción:** hasta que se redespliegue el backend con `WebConfig`, la API
  desplegada responde `403 Invalid CORS request` a peticiones del navegador desde otro
  origen. Las pruebas de lectura con `curl` sí funcionan.
- **Huella de carbono:** requiere que el backend con `CarbonoController` esté desplegado
  y que `esquema_carbono.sql` se haya aplicado. Si falta la configuración de base de
  datos, el módulo muestra el error de API y no simula resultados.
- **Árbol AVL:** la API devuelve el recorrido inorden y la altura, no la forma interna del
  árbol. La vista muestra el índice ordenado y las métricas reales, sin dibujar nodos
  inventados.
- **Pila de acciones:** se persiste en PostgreSQL. El AVL y la cola se reconstruyen desde
  los equipos activos y las órdenes pendientes.

## 9. Verificación sugerida

```bash
node --check js/api.js
node --check js/app.js
node --check js/views/ordenes.js
```

Prueba de contrato de solo lectura:

```bash
node --input-type=module -e "
import { api } from './js/api.js';
const [estado, areas, equipos, ordenes, cola, historial, stats] = await Promise.all([
  api.estado(), api.areas.listar(), api.equipos.listar(), api.ordenes.listar(),
  api.ordenes.cola(), api.ordenes.historial(), api.estadisticas()
]);
console.log({ areas: areas.length, equipos: equipos.length, ordenes: ordenes.length,
  cola: cola.length, historial: historial.length, total: stats.totalOrdenes });
"
```

Las operaciones de escritura deben probarse contra una base de datos de prueba o con
autorización explícita, porque modifican la base compartida.
