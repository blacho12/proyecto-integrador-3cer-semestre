# Implementación: gestión de áreas y huella de carbono

Proyecto: Sistema de Gestión de Mantenimiento Industrial (Grupo 7).
Implementación realizada directamente sobre el código existente, sin reconstruir la
interfaz ni reemplazar las estructuras de datos.

---

## 1. Diagnóstico de la arquitectura

La aplicación es una aplicación de escritorio Java Swing con separación en capas:

| Paquete | Responsabilidad |
|---|---|
| `modelo` | Entidades del dominio: `Area`, `Equipo`, `OrdenMantenimiento`, `Criticidad`. |
| `estructuras` | Implementaciones propias: `ArbolAVL`, `ColaPrioridad`, `PilaAcciones`. No se modificaron. |
| `datos` | Acceso JDBC a PostgreSQL con `PreparedStatement`: `AreaDAO`, `EquipoDAO`, `OrdenDAO`, `ConexionBD`. |
| `app` | Lógica de negocio y transacciones: `GestorOrdenes`, pruebas ejecutables. |
| `ui` | Ventana, paneles, componentes y tema visual. Los paneles no ejecutan SQL. |

El flujo existente es: **panel → GestorOrdenes → DAO → PostgreSQL**. PostgreSQL es la
fuente de verdad; cada operación hace `commit` y, si falla, `rollback`. El árbol AVL, la
cola de prioridad y la pila de acciones siguen funcionando igual que antes.

Diagnóstico previo relevante: `AreaDAO` ya tenía insertar, consultar, actualizar y
eliminar. Faltaba la interfaz CRUD completa y las validaciones de duplicados/equipos
asociados. No existía ningún concepto de consumo energético ni de emisiones.

---

## 2. Funcionalidad 1: huella de carbono

### 2.1 Fórmulas y precisión

`app.CalculadoraCarbono` implementa exclusivamente las fórmulas, sin acceso a base de
datos:

```
consumo adicional (kWh) = consumo normal × horas en mal estado × porcentaje extra / 100
emisiones (kg CO₂)      = consumo adicional × factor de emisión
```

Todos los valores son `BigDecimal`; el consumo adicional y las emisiones se redondean a
4 decimales con `RoundingMode.HALF_UP` (coincide con `NUMERIC(14,4)`). El ejemplo
(10 kWh/h × 8 h × 15 % × 0,15 kg/kWh) produce **12 kWh y 1,8 kg CO₂**.

Se rechazan valores nulos o negativos antes de calcular.

### 2.2 Datos y persistencia

- `equipo.consumo_normal_kwh NUMERIC(10,2)`: consumo normal por hora, asociado al equipo.
- `equipo.porcentaje_extra NUMERIC(5,2)`: porcentaje adicional estimado en mal estado.
- `configuracion_planta`: una sola fila (`id_configuracion = 1`) con
  `factor_emision NUMERIC(10,4)`, configurable desde la vista.
- `registro_carbono`: `id_registro`, `codigo_equipo` (FK a `equipo` con
  `ON DELETE RESTRICT`), `horas_mal_estado`, `consumo_adicional_kwh`,
  `emisiones_kg_co2`, `factor_emision` y `fecha_registro`.

El factor se guarda en **cada registro** para conservar la trazabilidad histórica. El
consumo normal y el porcentaje no se duplican en cada registro: permanecen en `equipo`.
La base también tiene restricciones `CHECK` para impedir negativos.

### 2.3 Clases nuevas y responsabilidades

| Archivo | Responsabilidad |
|---|---|
| `app/CalculadoraCarbono.java` | Fórmulas puras y validación de negativos. |
| `datos/CarbonoDAO.java` | Insertar/consultar registros, leer y actualizar el factor, todo con `PreparedStatement`. |
| `modelo/RegistroCarbono.java` | Fila histórica con nombres de equipo/área resueltos por JOIN para mostrar. |
| `modelo/ResumenCarbono.java` | Consolidado inmutable calculado con los registros persistidos. |
| `ui/paneles/PanelHuellaCarbono.java` | Formulario, indicadores, tablas por área/máquina e historial. |

### 2.4 Lógica en `GestorOrdenes`

- `configurarParametrosEquipo`: valida no negativos y porcentaje ≤ 100, normaliza a la
  escala de la base, actualiza PostgreSQL y el objeto del AVL en memoria.
- `registrarEmisionCarbono`: consulta el equipo y el factor vigente, calcula con
  `CalculadoraCarbono`, inserta el registro, guarda el factor y confirma la transacción.
- `actualizarFactorEmision` / `consultarFactorEmision`: configuración de la planta.
- `calcularResumenCarbono`: lee **todos** los registros de PostgreSQL y consolida total,
  emisiones por área, emisiones por equipo y cantidad de máquinas con emisiones > 0.

No se usan valores simulados: si no hay registros, los indicadores muestran cero.

### 2.5 Vista "Huella de carbono"

Incluye:

- Selección de equipo registrado y consulta de sus parámetros actuales.
- Edición y guardado del consumo normal y del porcentaje extra del equipo.
- Registro de horas en mal estado y cálculo/persistencia de emisiones.
- Factor de emisión configurable de la planta.
- Indicadores: total de la planta, máquinas con emisiones y cantidad de registros.
- Tabla de emisiones por área.
- Tabla de emisiones por máquina.
- Tabla histórica con código, equipo, área, horas, consumo adicional, emisiones y fecha.

La vista se recarga desde PostgreSQL después de cada operación confirmada.

---

## 3. Funcionalidad 2: gestión completa de áreas

Se amplió `AreaDAO` con:

- `existeNombre(nombre, excluirIdArea)`: comparación sin distinguir mayúsculas.
- `contarEquipos(idArea)`: verificación previa al borrado.
- `contarEquiposPorArea()`: conteo para la tabla.

Se amplió `GestorOrdenes` con:

- `registrarArea`: nombre obligatorio, máximo 100 caracteres y duplicados rechazados.
- `modificarArea`: valida existencia, nombre, longitud y duplicados; actualiza PostgreSQL.
- `eliminarArea`: comprueba equipos asociados. Si existen, **no elimina** y explica que
  primero deben reasignarse o eliminarse. La FK `fk_equipo_area` con
  `ON DELETE RESTRICT` actúa como segunda barrera. No hay borrado en cascada.
- `contarEquiposPorArea`.

`ui/paneles/PanelAreas.java` ofrece:

- Tabla con ID, nombre, descripción y número de equipos.
- Formulario que se carga al seleccionar un área.
- Botones Agregar, Modificar, Eliminar y Nuevo/limpiar.
- Validaciones y confirmación antes de eliminar.
- Mensaje específico cuando el área tiene equipos asociados.
- Recarga automática de la tabla y del conteo después de cada operación.

También se añadieron las entradas **Gestión de áreas** y **Huella de carbono** al menú
lateral de `ui/MenuPrincipal` y se registraron sus vistas.

---

## 4. Clases existentes modificadas

| Archivo | Cambio y responsabilidad |
|---|---|
| `app/GestorOrdenes.java` | Se agregó la orquestación de carbono (parámetros, factor, cálculo, registro y consolidado) y las validaciones CRUD de áreas con `commit`/`rollback`. Conserva intactas las operaciones de órdenes y deshacer. |
| `datos/AreaDAO.java` | Nuevas consultas `existeNombre`, `contarEquipos` y `contarEquiposPorArea`, todas con `PreparedStatement`. |
| `datos/EquipoDAO.java` | Inserta/consulta las columnas de consumo y agrega `actualizarParametrosCarbono`; el resto del CRUD no cambia. |
| `modelo/Equipo.java` | Nuevos campos `BigDecimal consumoNormalKwh` y `porcentajeExtra`, con un constructor compatible con el código existente. |
| `ui/MenuPrincipal.java` | Nuevas entradas de navegación y registro de `PanelAreas` y `PanelHuellaCarbono`. |
| `datos/ConexionBD.java` | El mensaje de clave foránea ahora menciona también registros de carbono asociados. |
| `LEEME.txt` | Instrucciones de migración, nuevas vistas y comando de la prueba. |

No se modificaron `pom.xml`, `esquema.sql`, el árbol AVL, la cola de prioridad ni la pila.

---

## 5. Script SQL

Archivo nuevo e idempotente:

```
src/main/resources/esquema_carbono_areas.sql
```

Hace lo siguiente:

1. Agrega `consumo_normal_kwh` y `porcentaje_extra` a `equipo` con `DEFAULT 0` para las
   filas existentes.
2. Agrega restricciones `CHECK` de no negativos mediante un bloque `DO` (PostgreSQL no
   admite `ADD CONSTRAINT IF NOT EXISTS`).
3. Crea `configuracion_planta` con una única fila y factor inicial `0.1500`.
4. Crea `registro_carbono` con FK a `equipo`, restricciones de no negativos e índices por
   equipo y fecha.

El archivo original `esquema.sql` no se modificó.

---

## 6. Pruebas realizadas

### 5.1 Prueba nueva: `app.PruebaCarbonoAreas`

Requiere PostgreSQL. Limpia sus propios datos al inicio y al final, y restaura el factor
de emisión original. Resultado de la ejecución final:

```
=== RESUMEN: 54 OK, 0 FALLAS ===
```

Cubre:

**Huella de carbono**
- Fórmula del ejemplo: 12 kWh y 1,8 kg CO₂.
- Rechazo de consumo, horas, porcentaje y factor negativos.
- Configuración de parámetros por equipo.
- Equipo A (10 kWh/h, 15 %): 8 h → 12 kWh / 1,8 kg; 2 h → 3 kWh / 0,45 kg.
- Equipo B (20 kWh/h, 10 %): 5 h → 10 kWh / 1,5 kg.
- Máquina C sin registros: no aparece en las emisiones por equipo.
- Dos máquinas en la misma área: consolidado por área = 3,75 kg.
- Consolidado de toda la planta = 3,75 kg (medido como delta sobre la línea base).
- Cantidad de máquinas con emisiones = línea base + 2.
- Persistencia tras crear una instancia nueva de `GestorOrdenes` (reinicio simulado):
  registros, factor y área modificada siguen disponibles.
- Cambio del factor a 0,20: el nuevo registro calcula 0,30 kg y conserva 0,20; los
  registros anteriores conservan 0,15. Trazabilidad comprobada.

**Gestión de áreas**
- Crear área y generar ID.
- Rechazar nombre vacío y duplicado exacto.
- Rechazar duplicado ignorando mayúsculas.
- Modificar nombre y descripción, verificando la fila en PostgreSQL.
- Rechazar un nombre ya usado por otra área.
- Eliminar un área sin equipos.
- Intentar eliminar un área con equipos: se rechaza, se explica el motivo y el área
  permanece.
- Persistencia del área modificada tras reiniciar.

### 5.2 Pruebas existentes (sin regresiones)

```
app.PruebaIntegracion  → === RESUMEN: 43 OK, 0 FALLAS ===
app.PruebaEstructuras  → salida completa, exit code 0
```

La prueba de integración existente siguió pasando después de agregar las columnas y
tablas nuevas. Las estructuras AVL, cola de prioridad y pila no se tocaron.

### 5.3 Verificación de interfaz

Se compiló todo el proyecto y se ejecutó un arnés headless que:

1. Construyó `PanelAreas` y `PanelHuellaCarbono` con un `GestorOrdenes` real.
2. Ejecutó `recargar()` en ambos.
3. Esperó la carga asíncrona desde PostgreSQL.

Resultado: `PanelAreas componentes=2`, `PanelHuellaCarbono componentes=2`,
`SMOKE UI OK`, sin errores en la barra de estado.

La evidencia textual completa está en `evidencia_pruebas_carbono.txt`.

---

## 7. Instrucciones para ejecutar

### Base de datos

Base nueva:

```bash
psql -U postgres -d mantenimiento -f src/main/resources/esquema.sql
psql -U postgres -d mantenimiento -f src/main/resources/esquema_carbono_areas.sql
```

Base existente: aplicar solo la migración (es idempotente):

```bash
psql -U postgres -d mantenimiento -f src/main/resources/esquema_carbono_areas.sql
```

### Variables de entorno

```bash
DB_URL=jdbc:postgresql://localhost:5432/mantenimiento
DB_USER=postgres
DB_PASSWORD=****
```

### Compilar y ejecutar la interfaz

```bash
mvn clean package
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes;$(cat target/cp.txt)" ui.MenuPrincipal
```

En Linux/macOS el separador del classpath es `:`:

```bash
java -cp "target/classes:$(cat target/cp.txt)" ui.MenuPrincipal
```

Luego usar las opciones **Gestión de áreas** y **Huella de carbono** del menú lateral.

### Ejecutar la prueba nueva

```bash
java -cp "target/classes;$(cat target/cp.txt)" app.PruebaCarbonoAreas
```

Requiere las mismas variables `DB_URL`, `DB_USER` y `DB_PASSWORD`.

---

## 8. Errores encontrados y soluciones

| Situación | Solución aplicada |
|---|---|
| PostgreSQL no admite `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS`. | Se usó un bloque `DO` que consulta `pg_constraint` antes de agregar cada `CHECK`. |
| Filas de `equipo` existentes al agregar columnas `NOT NULL`. | Se agregaron con `DEFAULT 0`; las filas previas quedan válidas. |
| Duplicados de área detectados solo por la restricción única, con mensaje genérico. | Se agregó `AreaDAO.existeNombre` con comparación `LOWER(...)` y mensajes claros en `GestorOrdenes`. La restricción única de PostgreSQL sigue siendo la última barrera. |
| Riesgo de eliminar un área con equipos. | Verificación previa con `contarEquipos` y FK `ON DELETE RESTRICT`; no se implementó cascada. |
| Eliminar un equipo con registros de carbono producía un mensaje genérico de FK. | Se amplió `ConexionBD.mensajeAmigable` para mencionar órdenes o registros de carbono asociados. |
| Desajuste entre la escala decimal usada en el cálculo y la almacenada (`NUMERIC`). | Horas, consumo y porcentaje se normalizan antes de calcular; resultados a 4 decimales. |
| La tabla histórica de 7 columnas quedaba comprimida al compartir fila con otra tabla. | La tabla de registros y la de emisiones por máquina ahora ocupan todo el ancho disponible. |
| No había Maven ni JDK 25 en el entorno de verificación. | Se compiló con `javac --release 21` usando el conector PostgreSQL y FlatLaf ya cacheados, y se ejecutó PostgreSQL 18 local. El `pom.xml` no se modificó. |

---

## 9. Limitaciones de la verificación

- No se ejecutó `mvn clean package` porque Maven no está instalado en este entorno y el
  JDK disponible es 21, mientras que el `pom.xml` declara `release 25`. La compilación se
  validó con `javac --release 21`; el código no usa APIs posteriores a Java 21.
- La interfaz se verificó de forma headless (construcción y carga de datos). No se hizo
  una inspección visual con pantalla ni capturas, porque el entorno no tiene display.
- La prueba de integración usa una base PostgreSQL local temporal; no se ejecutó contra
  una base de producción.
