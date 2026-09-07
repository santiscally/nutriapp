# 07 — Maestro de artículos de TBC + ajustes de catálogo (mail de Gon, 2026-08-03)

> **Fuente:** mail de Gon del 2026-08-03 con el Excel adjunto.
> **Archivo:** `instrucciones_claude/maestro-articulos-tbc-2026-08-03.xlsx` (**git-ignored**, ver §0).
> Desbloquea la **Ola 3** de `06-cambios-post-demo-2026-07-31.md` (C-10 a C-13) y agrega 3 ajustes
> nuevos del sync de Contabilium.
>
> Todos los números de este documento salen de medir el Excel real contra el catálogo ya sincronizado
> en la DB local (2267 productos traídos de la cuenta viva de Contabilium el 2026-07-28).

---

## 0. Por qué el Excel no se commitea

De las 126 columnas del maestro, bonosapp usa **9**. Las otras traen **costo, margen, comisión, precio de
lista por proveedor y precios de transfer de droguería** de 2225 artículos: es la estructura de costos de
TBC. Sigue la convención que el repo ya tiene para `presupuesto_bonosapp.pdf` → entrada en `.gitignore`.
El archivo vive en la carpeta pero se comparte por fuera del repo. Este documento (sin un solo número de
costo) es lo que sí queda versionado.

**Consecuencia de diseño:** el importador **lee solo las 9 columnas y descarta el resto**. Los costos y
márgenes de TBC nunca entran a la base de bonosapp.

---

## 1. Qué mandó Gon

Dos cosas en un mail:

1. **El Excel maestro** ("es sagrado", call `26:58`), con una hoja extra —`Columnas usables en Nutriapp`—
   donde marca las 9 columnas que le interesan. Más el mecanismo que imagina: *"un 'examinar' en el usuario
   admin e insertar el Excel y que tire algún mensaje de 'Archivo importado correctamente'"*.
2. **Seis definiciones sobre el endpoint de Contabilium** (código de barras, filtro de estado, de tipo,
   de rubro, subrubro=marca, Nombre vs Descripción). Tres ya están hechas, tres son trabajo nuevo — §4.

---

## 2. El Excel, medido

`Maestro ejemplo y columnas.xlsx` — 2 hojas, **2225 filas** de artículos, **126 columnas**.

### 2.1 La clave de cruce (verificada, no asumida)

Gon escribe *"SKU: CÓDIGO ARTÍCULO VINCULA CON CÓDIGO CONTABILIUM"*. Confirmado contra la DB:

| | |
|---|---|
| Excel `SKU` == `productos.sku` == Contabilium `Codigo` | ✅ |
| Match por SKU | **2163 / 2225 (97,2 % del Excel, 95,4 % del catálogo)** |
| Filas del Excel sin producto en el catálogo | 62 |
| Productos del catálogo sin fila en el Excel | 104 |
| Excel `ID CONTABILIUM` vs `productos.contabilium_id` en los 2163 matcheados | **0 discrepancias** |

O sea: hay **doble clave** y las dos coinciden. El importador cruza por SKU y usa `ID CONTABILIUM` como
verificación — si para un SKU los dos no coinciden, esa fila se rechaza y se reporta en vez de pisar el
producto equivocado.

### 2.2 Las 9 columnas y dónde caen

| Columna del Excel | Valores distintos | Destino en bonosapp | Estado |
|---|---|---|---|
| `SKU` | 2225 (únicos) | clave de match | ya existe |
| `DEPARTAMENTO` | 6 | `productos.departamento` **(nuevo)** | filtro nivel 1 |
| `CATEGORIA` | 23 | `productos.categoria` **(se resignifica, ver §3.1)** | filtro nivel 2 |
| `SUBCATEGORIA` | 142 | `productos.subcategoria` **(nuevo)** | filtro nivel 3 |
| `ELABORADOR / FABRICANTE` | 128 | `productos.laboratorio` (la columna ya existe, hoy vacía) | filtro |
| `ESTADO` | 2 (`ACTIVO` / `BLOQUEADO`) | `productos.bloqueado_maestro` **(nuevo)** → despublica | regla |
| `LINK IMAGEN TIENDA NUBE` | 174 filas | `productos.imagen_url` (ya existe, hoy vacía) | UI |
| `DESCRIPCION WEB` | 537 filas | `productos.descripcion_web` **(nuevo)**, hasta 2283 chars | UI "más info" |
| `TAGS TIENDANUBE` | 558 filas / 2420 tags crudos | tabla `producto_tags` **(nueva)** | buscador |

### 2.3 Cobertura real sobre lo que hoy se puede recetar

De los **728 productos publicados** hoy, **722 están en el Excel**. Sobre esos 722:

| Campo | Cobertura |
|---|---|
| DEPARTAMENTO / CATEGORIA / SUBCATEGORIA / ELABORADOR | **722 (100 %)** |
| TAGS | 539 (74,7 %) |
| DESCRIPCION WEB | 518 (71,7 %) |
| LINK IMAGEN TIENDA NUBE | **174 (24,1 %)** |

Los cuatro campos de filtrado vienen completos: **los filtros de C-11 van a funcionar para todo el
catálogo recetable**. La imagen, en cambio, cubre 1 de cada 4 productos → la UI tiene que estar diseñada
para el caso "sin imagen" como el normal, no como la excepción.

### 2.4 Lo que el Excel **no** tiene

El presupuesto firmado promete buscadores *"por principio activo, presentación, marca, laboratorio, etc."*.
Con el maestro en la mano:

- **Laboratorio** → ✅ `ELABORADOR / FABRICANTE`, 100 % de cobertura.
- **Marca** → ✅ ya sale del subrubro de Contabilium (Gon lo reconfirma en el mail).
- **Presentación** → ⚠️ no existe como campo. Lo más cercano es `CONTENIDO NETO` + `UNIDAD DE CONTENIDO`,
  con **12 % de cobertura** (262 de 2225). No alcanza para un filtro.
- **Principio activo** → ❌ no existe en ninguna de las 126 columnas.

**Lo cubren los tags.** El ejemplo que dio Gon en la call (`29:15`) fue justamente buscar *"magnesio"*, y
`magnesio` está como tag en 39 artículos. Los tags son, en los hechos, el índice de principio activo /
propiedad del producto. Es el argumento de peso para hacer C-10 (búsqueda por tags con ranking) bien y no
como un `OR` suelto. La subcategoría aporta el eje terapéutico (`HUESOS Y ARTICULACIONES`,
`INVIERNO Y DEFENSAS`, `CONTROL Y REDUCTORES DE PESO`).

### 2.5 Los tags, en detalle

- **Separador: salto de línea** dentro de la celda (no coma). Máximo 499 caracteres por celda.
- 2420 tags crudos distintos, con el mismo tag repetido en distintas capitalizaciones:
  `salud` (436) y `Salud` (51), `bienestar` (424) y `Bienestar` (58).
- → Se guardan **dos formas**: la original para mostrar y una normalizada (trim + lowercase + unaccent)
  para indexar y deduplicar. Sin eso el filtro por tag muestra `salud` y `Salud` como dos cosas distintas.

---

## 3. Los tres choques con lo que ya está construido

### 3.1 `categoria` ya está ocupada, y ocupada mal

Hoy `productos.categoria` se puebla con el **Rubro de Contabilium**. Distribución real del catálogo:

| Rubro (hoy `categoria`) | Productos | Publicados |
|---|---|---|
| Producto terminado | 2170 | 727 |
| Servicios | 49 | 0 |
| Insumos | 36 | 1 |
| Materias primas / Material PoP / Gastos | 12 | 0 |

O sea: **el filtro "categoría" del emisor tiene un solo valor útil para el 99,8 % de lo recetable.** Es
decorativo. Y el mail de Gon lo remata: el rubro pasa a ser un **filtro de ingreso** (solo entra 144331),
con lo cual como faceta deja de existir por completo.

**Decisión:** el rubro de Contabilium se muda a una columna propia `productos.rubro` (dato del ERP, sirve
para el filtro de ingreso y para auditar) y `categoria` pasa a significar la **CATEGORIA del Excel**
(23 valores reales). Migración: `rubro := categoria; categoria := NULL`, y el sync deja de escribir
`categoria`.

### 3.2 Dos escritores sobre la misma fila

El sync de Contabilium y el importador del Excel escriben el mismo `productos`. Para que ninguno pise al
otro, **cada campo tiene un único dueño** y no hay intersección:

| Dueño | Campos |
|---|---|
| **Contabilium** (sync) | `nombre`, `descripcion`, `precio`, `stock`, `marca` (subrubro), `rubro`, `codigo_barras`, `contabilium_id`, `activo_erp`, `last_synced_at` |
| **Excel maestro** (import) | `departamento`, `categoria`, `subcategoria`, `laboratorio`, `descripcion_web`, `imagen_url`, `bloqueado_maestro`, tags, `maestro_synced_at` |

Con esta partición el orden de ejecución deja de importar: se puede sincronizar y después importar, o al
revés, y el resultado es el mismo. (El orden operativo que pidió Gon —Contabilium primero, después el
Excel— sigue siendo el recomendado, pero por cobertura de match, no por corrección.)

`publicado` deja de ser un campo que alguien escribe y pasa a ser **derivado**, recalculado por los dos
procesos con la misma función:

```
publicado = precio >= 100                    (regla vieja, confirmada en la call 44:52)
          AND estado_erp != 'Inactivo'       (C-14, ya hecho)
          AND tipo_erp == 'Producto'         (mail, nuevo)
          AND rubro_id ∈ rubros_permitidos   (mail: 144331, nuevo)
          AND bloqueado_maestro == false     (Excel, nuevo)
```

### 3.3 El producto que no está en el Excel

104 productos del catálogo no tienen fila en el maestro. **Default propuesto: no los bloquea** — quedan
recetables con los campos nuevos en `null`. Bloquear por ausencia es peligroso: si Gon sube un Excel
recortado, se vacía el catálogo. Pendiente de confirmar con él (§6).

---

## 4. Las 6 definiciones de Contabilium del mail

| # | Lo que pidió Gon | Estado |
|---|---|---|
| M-1 | Mostrar el código de barras (`"CodigoBarras": "7790839000267"`) | ❌ **nuevo** — el campo viene en el payload (verificado en la cuenta viva) pero no se mapea ni se persiste |
| M-2 | Solo `"Estado": "Activo"` | ✅ **hecho** (C-14, `ProductoSyncService.esInactivo`) |
| M-3 | Solo `"Tipo": "Producto"` | ❌ **nuevo** — el campo se lee pero no se filtra. **Ojo: ese campo tiene tres valores, no dos** (§6.1) |
| M-4 | Solo `"IdRubro": "144331"` (producto terminado) | ❌ **nuevo** — es C-13, ahora con el ID concreto |
| M-5 | Subrubro = Marca, y así debe mostrarse | ✅ **hecho** (sync mapea subrubro→`marca`; el filtro ya dice "Marca") |
| M-6 | Usar `Nombre`, no `Descripcion`, para el nombre | ✅ **ya es así** (`ConceptoDto.Nombre` → `producto.nombre`) |

Notas:

- **M-1:** 2009 de 2225 artículos tienen código de barras. Además de mostrarlo conviene hacerlo
  **buscable**: quien tiene el producto en la mano escanea o tipea el código y espera encontrarlo.
- **M-3:** también va por configuración (`bonosapp.catalogo.tipos-erp-permitidos`) y como **lista**, no
  como valor único, justamente por lo de §6.1.
- **M-4:** el ID va en configuración (`bonosapp.catalogo.rubros-permitidos`), **no hardcodeado** — Gon
  quedó en pasar la lista completa de rubros que cuentan como producto terminado (call `34:26`) y hoy
  tenemos uno solo.

---

## 5. Impacto medido de aplicar todo

No es una estimación: esto es lo que pasó al correr el importador y el sync contra la base real
(2267 productos traídos de la cuenta viva de Contabilium).

| Paso | Publicados | Δ |
|---|---|---|
| Antes de todo | 728 | — |
| Después de importar el maestro (`ESTADO = BLOQUEADO`) | 702 | **−26** |
| Con `Tipo=Producto` solo (la instrucción literal del mail) | 496 | −206 |
| **Configuración final: `Tipo ∈ {Producto, Combo}` + rubro 144331** | **699** | **−3** |

Los −206 de la fila del medio eran **casi todos combos** — por eso se dejaron dentro (§6.1); el rubro
solo se llevó un insumo suelto. El catálogo recetable queda en **699 de 2267**, con 203 combos. Los
otros 1568 ya estaban afuera por el filtro de precio &lt; $100 (los ~1000 artículos cargados a $1) y
por estado inactivo en el ERP.

---

## 6. Preguntas para Gon (bloquean parte de la Ola 3)

### 6.1 ⚠️ Los combos: ¿se recetan? — *(resuelto por ahora, falta confirmar con Gon)*

El `Tipo` de Contabilium **no tiene dos valores, tiene tres**. Medido en la cuenta real:

| `Tipo` | Artículos |
|---|---|
| `Producto` | 2004 |
| `Combo` | **209** |
| `Servicio` | 54 |

Los `Combo` son los packs x2, x3, x4 y los exhibidores — incluida la línea ON-ROLL propia de TBC.
O sea: *"solo quedarnos con Producto"*, aplicado tal cual, **no saca solo los servicios: también saca
los 209 combos**. Medido: el catálogo recetable cae de 702 a 496 (−29 %).

**Decisión tomada (Santi, 2026-08-03): van los dos** — `CATALOGO_TIPOS_ERP=Producto,Combo`. El pedido de
Gon apuntaba a sacar los servicios, no los packs: los combos se venden en TiendaNube como cualquier otro
artículo y el cupón les aplica igual, así que excluirlos sería perder el 29 % del catálogo por un efecto
colateral de cómo el ERP usa ese campo.

- **Igual hay que consultárselo**, porque la instrucción literal decía otra cosa.
- **Volver a su versión no es tocar código**: `CATALOGO_TIPOS_ERP=Producto` y re-sincronizar (→ 496).

### 6.2 Las otras

1. **Productos que están en Contabilium pero no en el Excel (104 hoy):** ¿se siguen mostrando?
   (propuesta: sí, ver §3.3).
2. **Marca:** queda del subrubro de Contabilium. El Excel trae una columna `MARCA` propia (243 valores,
   con "SIN MARCA" en 458 filas) que Gon **no** marcó como usable. ¿Confirmamos que la buena es la de
   Contabilium? *(Dato: el filtro de marca hoy tiene 125 valores sobre lo publicado, contra 35 de
   laboratorio — las dos cosas conviven bien, no hace falta elegir.)*
3. **Lista completa de rubros "producto terminado"** — hoy tenemos 144331 y nada más (pendiente desde la
   call).
4. **Imágenes:** solo el 24 % de lo recetable tiene link. ¿Va igual, con placeholder en el resto?
   (Nota: esto **revive C-16 sin su costo** — el link apunta al CDN de TiendaNube, no guardamos nada.)
5. **`SUBESTADO`:** 461 filas dicen `DEFINIR SI PASAR ACTIVO`. ¿Se ignora la columna y manda solo `ESTADO`?
   (propuesta: sí).
6. **Frecuencia:** confirmado botón manual a demanda (call `30:46` + el mail). El link de OneDrive vía
   Graph API queda descartado para el MVP.

---

## 7. Plan de ejecución — Ola 3 (reemplaza el bloque "Ola 3" de `06-...md`)

Nada de esto depende de TiendaNube. Lo único bloqueado son las decisiones de §6.

> **Estado al 2026-08-03: la Ola 3 está completa** — 3.A a 3.F, back y front. `mvn test` 141 unit
> BUILD SUCCESS; front `tsc`/`oxlint`/`build` verdes. Verificado end-to-end contra el catálogo real
> (§8). **Pendiente: mirar las pantallas nuevas en vivo** (back `:8088`, front `:5174`).

### 3.A — Ajustes del sync de Contabilium ✅ *(no depende del Excel)*

1. **Migración `V009`**: `codigo_barras`, `departamento`, `subcategoria`, `descripcion_web`, `rubro`,
   `rubro_id`, `bloqueado_maestro`, `maestro_synced_at` en `productos`; tabla `producto_tags`; tabla
   `maestro_importaciones`. Data-migration `rubro := categoria`, `categoria := NULL`. Índices para los
   filtros nuevos.
2. **M-1** — mapear `CodigoBarras` en `ConceptoDto`/`Concepto`, persistirlo, exponerlo en
   `ProductoResponse` y sumarlo al texto buscable de `q`.
3. **M-3 + M-4 (C-13)** — persistir `tipo` y `rubro_id`; regla de publicación con `Tipo=Producto` y
   `rubro_id ∈ bonosapp.catalogo.rubros-permitidos` (configurable, default `144331`).
4. **Refactor de `publicado`** a la función derivada de §3.2, compartida por sync e import.
5. Tests: sync con concepto Servicio, con rubro no permitido, con código de barras, y el recálculo de
   `publicado` combinando reglas de las dos fuentes.

### 3.B — C-12: importador del maestro ✅

6. **Parser** (`modules/producto/maestro/`): lectura **por nombre de columna, no por posición** — son 126
   columnas y Gon va a seguir editando la planilla; si se rompe el orden no puede corromper los datos.
   Hoja objetivo `Maestro`. Si falta una columna esperada → error 422 con la lista, sin escribir nada.
   Librería: `fastexcel-reader` (streaming, ~200 KB) antes que Apache POI (12 MB de deps para leer 9 columnas).
7. **Endpoint** `POST /api/v1/admin/productos/importar-maestro` (multipart, `admin:manage`), límite de
   tamaño y whitelist de content-type reusando el subsistema de archivos de C-08. **El archivo no se
   persiste**: se parsea, se aplican las 9 columnas y se descarta (§0).
8. **Reporte de importación** como respuesta y en `maestro_importaciones`: filas leídas, matcheadas,
   actualizadas, sin match (con la lista de SKUs), filas rechazadas por discrepancia de `ID CONTABILIUM`,
   columnas faltantes. Es lo que convierte el *"archivo importado correctamente"* de Gon en algo
   accionable: si el 40 % no matcheó, tiene que enterarse.
9. **Idempotente y re-ejecutable**; sella `maestro_synced_at` y recalcula `publicado`.
10. Tests: xlsx mínimo de fixture (match, sin match, discrepancia de ID, columna faltante, celda vacía,
    tags multi-línea, re-import sin cambios).

### 3.C — C-11: filtros del catálogo ✅ *(backend)*

11. `GET /productos` suma `departamento`, `subcategoria`, `laboratorio` (y `categoria` cambia de
    significado). `GET /productos/filtros` devuelve las listas nuevas; departamento→categoría→subcategoría
    en cascada (la subcategoría sin acotar son 142 valores en un dropdown: inusable).
12. Front: los selects nuevos en el buscador del emisor.

### 3.D — C-10: búsqueda por tags con ranking ✅

13. `q` matchea nombre, descripción, SKU, **código de barras** y **tags**, pero ordenado: match en nombre
    primero, después descripción, último tags — el pedido textual de Gon (`29:15`).
14. Filtro por tag exacto (click en un tag) como extra barato.

### 3.E — Imagen y "más info" ✅ *(revisa C-16)*

15. `imagenUrl` → **miniatura en cada fila** del buscador. La columna existe siempre, con un placeholder
    cuando no hay imagen: si apareciera solo en el 24 % de las filas, la lista quedaría desalineada.
16. `descripcionWeb` → botón **"Más info"** que abre un modal con la imagen grande, los datos del
    artículo (SKU, código de barras, laboratorio, marca, categoría), el texto largo y los tags.
    **Click en un tag = buscar por ese tag**: es la navegación "por propiedad" (magnesio, vegano) que
    el catálogo no tiene como campo. Es lo que describe la hoja de Gon, sin costo de storage — la
    imagen es un link al CDN de TiendaNube.

### 3.F — Front del import ✅

17. `MaestroImportCard` en `/integraciones`, al lado del sync de Contabilium: input file + botón
    "Importar maestro" + el reporte del punto 8, con la última importación arriba y el orden operativo
    escrito en la card. Si quedaron SKUs sin match se pueden desplegar. **Cuando hay filas sin match o
    rechazadas el toast sale como advertencia, no como éxito**: es exactamente el caso en el que un
    "importado correctamente" verde engañaría.

> **Nota de propiedad:** los puntos 12, 15, 16 y 17 tocan `frontend/`, que es de Fran. Se hicieron con el
> mismo criterio que C-06/C-07/C-09 durante sus vacaciones, y quedan avisados en el DIARIO.

---

---

## 8. Verificación end-to-end (2026-08-03)

Contra el stack real, con el archivo que mandó Gon y el catálogo vivo de Contabilium:

| Qué | Resultado |
|---|---|
| Import del maestro real (2225 filas, 1,5 MB) | 2163 aplicadas, 62 sin match, **0 rechazadas** |
| ¿Coincide con el análisis offline? | sí, exacto (§2.1) |
| `ID CONTABILIUM` discrepante | 0 filas en 2163 |
| Despublicados por `ESTADO = BLOQUEADO` | 26 |
| Tags cargados | 8447 (1996 distintos, 539 productos) |
| Sync de Contabilium **después** del import | no pisó ni un campo del maestro (§3.2 verificado) |
| Buscador `q=magnesio` | 41 resultados, los de nombre arriba, los de solo-tag al final |
| Buscador por código de barras | `7798349830060` → ON-ROLL FLOW |
| Filtros | 4 departamentos / 14 categorías / 72 subcategorías / 35 laboratorios / 125 marcas |
| Con combos habilitados (config final) | 699 recetables, 203 combos |
| Frontend | `tsc -b`, `oxlint` y `vite build` verdes |
| `mvn verify` | 141 unit + 1 IT, y las 9 migraciones aplican limpias sobre una Postgres nueva |

Un bug encontrado por los tests y arreglado: un archivo que no fuera un `.xlsx` legible salía como
**500 "error interno"** en vez del 422 con instrucciones (el `IOException` del zip se escapaba como
`UncheckedIOException`).

**Lo que falta verificar:** las tres pantallas nuevas en vivo (card de import, filtros en cascada,
modal de "más info"). Está todo verde por contrato —typecheck, lint, build y las respuestas reales de
la API— pero nadie las miró todavía.

---

## 9. Qué queda igual

- El **modo de carga es manual a demanda** (botón), no un job diario. Confirmado en la call y en el mail.
- **Marca ← subrubro de Contabilium.** No cambia.
- **Nombre ← `Nombre` de Contabilium.** Ya era así.
- El **filtro de precio < $100** y el filtro de stock opcional siguen como están (call `44:52`, C-15).
