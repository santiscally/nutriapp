# 06 — Cambios post-demo (call del 2026-07-31 con Gon + Leo)

> **Fuente:** `transcripcion-2026-07-31-call-gon-leo.pdf` (+ `.txt` para grepear). Call de 59 min,
> participantes: Santi, GB (Gon), LAC (Leo). La demo de la plataforma arranca en el minuto **16:00**.
> Cada ítem de abajo lleva el/los timestamp(s) donde se decidió, para poder volver al audio si hay dudas.
>
> **Ojo:** la primera parte de la call (04:05 → 12:57, ~9 min, Santi con Gon antes de que entrara Leo)
> **no quedó transcripta**. Ahí se habló del *otro* proyecto. Ese proyecto se movió a su propia carpeta: `../../datawarehouse-contabilium/docs/nuevo-proyecto-tbc-insumos.pdf`.

---

## Resumen ejecutivo

17 cambios: 16 confirmados en la call + **C-17** (foto de perfil), que agregó Santi después.
Ninguno es un rediseño: la arquitectura y el modelo aguantan todo.
Los que más pegan son tres, y los tres tocan plata:

1. **La comisión se calcula sobre lo que realmente pagó el paciente en TiendaNube**, no sobre el precio
   de Contabilium. Los descuentos son **acumulativos** (15% de la receta + 30% de promo de tienda = 45%),
   así que el valor estimado en la emisión puede no tener nada que ver con el final.
2. **Los precios desaparecen de casi toda la app.** Solo se ven en el buscador de la pantalla de emisión,
   con leyenda de "aproximado". Ni en el histórico de recetas, ni en el detalle, ni en lo que recibe el paciente.
3. **Aparece la liquidación**: un estado terminal nuevo (`LIQUIDADA`) y un cierre consolidado del lado admin,
   con exportable, para pagarle a cada nutricionista.

**Alcance vs. presupuesto firmado** (`presupuesto_nutriapp.pdf`, $2.000.000, 2 meses):

- **Ya estaba comprometido**: los buscadores "por principio activo, presentación, marca, laboratorio, etc."
  están escritos en el presupuesto. Como Contabilium **no tiene esos campos**, la ingesta del Excel maestro
  (C-12) es la única forma de cumplir lo firmado → entra sí o sí, aunque sea trabajo que no estaba estimado.
- **Fuera del alcance firmado** (candidatos a negociar o a v1.1): C-01 (% por nutricionista), C-05 + C-06
  (liquidación y cierre consolidado del admin con exportable), C-08 (matrícula + adjunto + datos fiscales).
  No es "no lo hago" — es "esto no estaba, decidamos si entra ahora o después".

---

## A. Precios y comisión

### C-01 — % de descuento y % de comisión configurables **por nutricionista**
`19:32–19:55`, `39:36–39:49`, `37:56–38:10`

Hoy son dos valores globales en `configuracion_sistema`. Leo pidió poder setearlos por nutricionista
("que una pueda cinco, otra pueda…"). Quién los setea: **solo el admin**, nunca la nutricionista (`19:29`).

- **DB**: `nutricionistas.descuento_porcentaje` y `nutricionistas.comision_porcentaje`, ambos *nullable*.
  `NULL` → cae al valor global. Así no hay que backfillear ni romper lo existente.
- **Backend**: resolución del % en `RecetaService` (nutricionista → global). Endpoint admin para editarlos.
- **Front**: campos en la ficha/aprobación de la nutricionista (ver C-09).
- **Decidir**: la receta ya emitida, ¿congela el % con el que se emitió? **Sí** — hay que persistir el %
  aplicado en la receta (snapshot), o cambiar un % rompe los cierres viejos.

### C-02 — Los precios solo se ven en la pantalla de emisión
`23:51–24:53`, `53:35–54:34`

Racional de Leo: "pensalo como una receta médica, no lleva precios". Y de fondo: no quieren que la
nutricionista se haga la cuenta de su comisión con un número que va a ser falso.

| Dónde | Precio |
|---|---|
| Buscador de productos (pantalla de emisión) | **Sí**, + leyenda tipo *"Los valores son aproximados y pueden cambiar sin previo aviso"* |
| Carrito/resumen de la receta que se está emitiendo | **Sí** (misma leyenda) |
| Listado de recetas | **No** |
| Detalle de una receta ya emitida | **No** — solo producto y cantidad |
| Receta que recibe el paciente (mail / WhatsApp / PDF) | **No** |
| Cierre mensual | Sí, pero **solo el dato real de TiendaNube** (ver C-03) |

- **Backend**: seguir persistiendo el snapshot de precio (hace falta para auditoría y para la comisión
  estimada interna), pero **dejar de exponerlo** en `RecetaResponse` / `RecetaItemResponse`.
- Confirmar si el admin sí puede verlos (yo diría que sí, es su ERP).

### C-03 — La comisión se calcula sobre el total facturado en TiendaNube
`22:18–22:49`, `52:23–52:44`, `54:34–54:55`

Ya estaba previsto que el webhook `order/paid` trajera el total, pero la call agrega algo que no teníamos
modelado: **los descuentos se acumulan**. El cupón de la receta (15%) se suma a la promo que la tienda ya
tenga (30%) → el paciente puede terminar pagando un 45% menos. Nada de lo que se ve en la emisión sirve
para calcular plata.

- Eliminar cualquier cálculo de comisión basado en el precio de Contabilium.
- Mientras la receta no convierta: comisión **$0 / "pendiente"**, nunca una estimación.
- **Riesgo a verificar en Fase 2**: hay que confirmar en TiendaNube que un cupón sea combinable con
  promociones de la tienda. Si no lo es, el escenario 15+30 no existe y hay que avisarles.

### C-04 — El cierre agrupa por fecha de conversión, no de emisión
`51:33–52:00`

Ejemplo textual de Gon: receta emitida el 29 de julio, pagada por el paciente en agosto → esa comisión
va al cierre de **agosto**.

- La fecha que manda es la del pago en TiendaNube (`recetas.fecha_conversion`).
- Revisar el `DashboardService` y el cierre mensual actual, que hoy agrupan por fecha de emisión.

---

## B. Liquidación (todo esto es nuevo)

### C-05 — Estado terminal `LIQUIDADA`
`55:14–55:22`, `57:29–58:44`

Ciclo nuevo: `PENDIENTE → APLICADA → LIQUIDADA` (+ `VENCIDA` / `ANULADA` como hoy).

- Una receta `LIQUIDADA` **deja de aparecer en los cierres siguientes** (`58:37`).
- Se liquida **por receta**, aunque la pantalla sea mensual: *"va a ser más por receta; por más que el
  cierre sea mensual, es por receta que se le liquida"* (`57:54`).
- Lo marca el **admin**, nunca la nutricionista (`57:43`).
- La nutricionista ve en su cierre si ya le pagaron ese mes (`57:29`).
- **Nombre a confirmar**: `LIQUIDADA` / `ABONADA` / `PAGADA`. Leo: *"abonada, la que quieras"* (`58:36`).
- **DB**: valor nuevo en `estado_receta` + `liquidada_at` + (opcional) `liquidacion_id` si querés agrupar.

### C-06 — Cierre consolidado del admin, con exportable
`55:28–55:57`, `56:45–57:14`

Pantalla nueva del lado admin (no existe hoy):

- Todas las nutricionistas juntas, **por rango de fechas configurable** — no solo mes calendario
  (*"un cierre mensual o de un corte de rango"*, `55:49`).
- Columnas pedidas explícitamente por Gon (`57:02–57:12`): **CUIT, mail, cuánto facturó, cuánto comisionó,
  cuántas recetas**.
- **Exportable a Excel/CSV** de un click.
- Desde ahí se marca como liquidado (dispara C-05).

### C-07 — El usuario admin **no** emite recetas
`56:17–56:45`

Gon: *"yo no dejaría que ese usuario pueda hacer recetas; nosotros nos creamos nuestra propia cuenta de
nutricionista si queremos"*.

- **Sacar del menú admin**: Dashboard, Emitir receta, Recetas, Pacientes.
- **Queda**: Cierres (consolidado), Nutricionistas, Integraciones, Configuración.
- Revisar authorities: hoy el ADMIN tiene de todo. Hay que separar de verdad los dos menús por rol,
  no solo esconder ítems en el front.

---

## C. Alta y gestión de nutricionistas

### C-08 — Campos nuevos en el registro + adjunto
`40:05–42:34`

Confirmaron el flujo que ya está (autoregistro → queda pendiente → el admin acepta, `38:48–39:21`).
Lo que cambia son los datos:

| Campo | Obligatorio | Nota |
|---|---|---|
| Nombre y apellido | Sí | ya está |
| Email | Sí | ya está |
| Celular | Sí | Leo: *"mail y celular obligatorio"* |
| DNI | Sí | para detectar duplicados (`40:40`) |
| **Matrícula nacional** | Sí | nombre del campo **a confirmar** — Leo se lo llevó para chequear si "nacional" corresponde (`42:34–43:12`) |
| **CUIT** | Sí | Gon corrige a Leo: **no pedir CUIL**, es para relación de dependencia (`57? → 42:13`) |
| **Condición fiscal** | Sí | desplegable: Responsable Inscripto / Monotributo / Exento… Gon manda la lista (`42:13–42:34`) |
| **Adjunto (título o matrícula)** | Sí | PDF o imagen; se guarda aunque después se apruebe a mano (`41:01–41:22`) |

- **DB**: columnas nuevas en `nutricionistas` + almacenamiento del archivo. Decidir dónde: volumen del VPS
  vs. objeto externo. Es un dato personal → definir retención y borrado.
- **Backend**: el `POST /registro` público pasa a multipart, o se parte en dos pasos (crear + subir).
- Validar dígito verificador del CUIT (barato y evita basura).

### C-17 — Foto de perfil de la nutricionista (avatar)
*No salió en la call — lo pide Santi después de la demo.*

El típico círculo con la foto. Hoy `AppLayout.tsx:62` muestra un `.avatar` con las **iniciales**; la idea es que
muestre la foto cuando exista y caiga a las iniciales cuando no.

- **No existe pantalla de perfil**: hay que crearla (`/perfil`), porque hoy la nutricionista no tiene dónde ver ni
  editar sus datos. Ahí va la subida de la foto.
- Dónde se ve el avatar: navbar (ya está el hueco), pantalla de perfil, y la ficha en la bandeja del admin.
- **Backend**: `nutricionistas.foto_url` (o id de archivo) + endpoint de upload. Validar tipo (jpg/png/webp) y
  tamaño máximo, y **generar un thumbnail** — no servir el original de 4 MB que suba alguien desde el celular.
- **Va junto con C-08**: la matrícula adjunta y la foto necesitan el mismo subsistema de archivos (dónde se
  guardan, cómo se sirven, permisos, borrado con la cuenta). Resolverlo una vez para los dos.
- Es dato personal: se borra con la cuenta.

### C-09 — Bandeja de nutricionistas con tabs
`39:26–40:05`

- Dos tabs: **Solicitudes pendientes** / **Aceptadas**.
- Al aceptar: ver los datos del registro, descargar el adjunto, y setear el **% de descuento** y el
  **% de comisión** de esa nutricionista (C-01).

---

## D. Catálogo de productos

### C-10 — Buscar también por tags, con ranking
`29:15–30:03`

Hoy busca por nombre, SKU y descripción (Leo lo probó y le cerró, `20:12`). Gon quiere sumar los **tags**
del maestro, pero con orden: *"arriba los que tienen magnesio en el nombre/descripción, más abajo los que
lo tienen en el tag"*.

- No es un `OR` plano: hay que rankear (peso por campo donde matchea).

### C-11 — Filtros por categoría, subcategoría y laboratorio
`28:44–30:23`

Hoy el único filtro tipo "marca" sale del **subrubro** de Contabilium (que TBC usa como marca, `20:38`).
Faltan **categoría, subcategoría, laboratorio y presentación** — que Contabilium no tiene y no deja cargar
(*"eso es una cagada de Contabilium en realidad"*, `29:07`).

> Esto está **en el presupuesto firmado** ("buscadores por principio activo, presentación, marca,
> laboratorio, etc."). O sea: no es un extra, es cumplir lo prometido — y depende del Excel.

### C-12 — Ingesta del "maestro de artículos" (Excel de OneDrive)
`25:26–31:15`, `59:04`

Es la base real de TBC: *"está actualizado todo, es sagrado"* (`26:58`, `30:23`). Tiene el código de
Contabilium en cada fila, así que **cruza por SKU/código** sin drama.

- **Modo de carga: manual a demanda.** Gon fue explícito: *"amerita un botón a demanda, no pongas algo
  diario"* (`30:46`). Va en la pantalla de Integraciones, al lado del sync de Contabilium.
- Orden operativo: sincronizar Contabilium → subir el Excel → se hace el match.
- **Alternativa a evaluar**: leerlo directo del link de OneDrive (Graph API) para que no tengan que subirlo
  a mano. Santi se lo llevó para pensar (`30:32`).
- **Bloqueado por el cliente**: Gon y Leo se llevaron de tarea definir **qué columnas** se importan (`59:04`).

### C-13 — Filtrar por rubro = "producto terminado"
`34:26–34:56`

Hay que quedarse **solo** con producto terminado; el resto (insumos, marketing, cosas internas) ni aparece.
El rubro sale del Excel maestro. Gon queda en pasar la lista de rubros válidos.

### C-14 — No recetar productos inactivos de Contabilium
`45:31–45:50`, `49:42`

Contabilium tiene un estado activo/inactivo que hoy no estamos mirando (nosotros filtramos por precio < $100).
Gon: los inactivos son cosas que ya no compran ni venden. Leo cierra el tema con *"que pueda recetar todo,
ya está, **si está activo en Contabilium**"*.

### C-15 — El stock **no** bloquea la receta
`44:08–44:24`, `46:01–49:42`

Debate largo, decisión final de Leo: se puede recetar sin stock. Se mantiene el filtro con/sin stock como
opción del buscador, y a Gon el dato le sirve para reponer (*"che, están recetando esto y no tengo, lo compro"*).

- **Nota técnica de Gon para más adelante** (`47:55–48:49`): el `getProductos` devuelve el stock **sumado de
  los 7 depósitos**, y TiendaNube vende de uno solo. El endpoint `getStockBySKU` da el stock por depósito.
  Leo lo mandó explícitamente a **v1.2** (`48:49`).
- Idea de Leo de un indicador visual de "sin stock": se descartó porque el dato no es live, es por sync.

### C-16 — Imágenes de producto: **fuera del MVP**
`31:22–33:30`

Gon: *"vale, MVP para mí es sin foto"*. La idea para después: ícono de cámara al lado del producto, click
y se abre la imagen, como portal de droguería. Son ~600 productos con foto y las tienen. Santi advirtió el
costo de storage en el VPS compartido; quedó anotado para evaluar, no para ahora.

---

## E. Confirmado en la call (no requiere trabajo)

- **Cupón restringido a los productos de la receta**: sí, así queda. Leo evaluó dejarlo abierto y lo
  descartó él mismo (*"le voy a dar más comisión de productos que no ameritan"*) — `23:05–23:49`.
- **Autoregistro + aprobación del admin**: es lo que ya está implementado. Confirmado (`38:48`).
- **Filtro de precio < $100**: les cerró, tienen ~1000 productos cargados a $1 (`44:52–45:31`).
- **Estados de receta y el listado**: OK con pendiente / aplicada / vencida / anulada (`51:06`).
- **Tipografía (Comic Neue)**: aprobada, con chiste incluido. No tocar (`16:11–17:31`).
- **Campos del paciente**: se pueden ampliar después, ya en producción. No es cambio inmediato (`18:05`).
- **TiendaNube**: confirmado que hace falta crear una app propia (no hay API directa). Están en el plan
  intermedio, que lo permite. Queda para el final, como estaba planificado (`35:00–37:19`).
- **Sync de Contabilium**: corrió en vivo, ~1 minuto para 2266 productos. Sin objeciones (`43:30–44:33`).
- **Tester**: van a sumar una nutricionista "conejillo de indias" cuando Santi diga que está listo (`18:20`).

---

## F. Pendientes del cliente (bloquean la ola 3)

| Qué | Quién | Bloquea |
|---|---|---|
| ~~Excel maestro de artículos + **qué columnas** se importan~~ | Gon + Leo | ✅ **llegó 2026-08-03** → `07-...md` |
| **¿Los combos se recetan?** (209 publicados en juego) | Gon | C-12 (regla de publicación) |
| Lista de rubros que cuentan como "producto terminado" (hoy solo `144331`) | Gon | C-13 |
| Lista de opciones de condición fiscal | Gon | C-08 |
| Nombre definitivo del campo matrícula ("nacional" o no) | Leo | C-08 |
| Valores reales de % de descuento y % de comisión | Leo | C-01 |
| App de TiendaNube creada en el Partner Portal + credenciales | Gon | Fase 2 completa |

---

## G. Preguntas abiertas (mías, para el próximo contacto)

1. Si el admin cambia el % de comisión de una nutricionista, ¿las recetas ya emitidas mantienen el % viejo?
   (Asumo que **sí** y lo snapshoteo — confirmar.)
2. El cierre del admin, ¿liquida por nutricionista+período de una, o receta por receta con checkboxes?
3. ¿El admin ve los precios en el detalle de receta, o los ocultamos también para él?
4. El adjunto de la matrícula: ¿lo tienen que poder ver después de aprobada, o solo en la validación?
   ¿Cuánto tiempo lo guardamos? (dato personal)
5. ¿El cupón de TiendaNube es combinable con las promos de la tienda? De esto depende que el escenario
   "15% + 30%" sea real (ver C-03).

---

## H. Plan de ejecución

Cuatro olas. Las dos primeras no dependen del cliente, así que arrancan ya.

### Ola 1 — Precios, roles y liquidación básica *(no depende de nadie)*
1. **C-07** — separar menú y permisos de admin vs. nutricionista.
2. **C-02** — sacar precios de listado, detalle y receta al paciente + leyenda en emisión.
3. **C-03 / C-04** — comisión solo sobre el dato real de TiendaNube, cierre por fecha de conversión.
4. **C-05** — estado `LIQUIDADA` + `liquidada_at` (migración + máquina de estados + tests).
5. **C-14** — excluir inactivos de Contabilium en el sync.

*Nota: C-03 se puede implementar entero contra el stub — el flujo de conversión ya existe.*

### Ola 2 — Config por nutricionista y cierre del admin
6. **C-01** — % por nutricionista con fallback al global + snapshot en la receta.
7. **C-09** — bandeja con tabs + set de % al aprobar.
8. **C-06** — cierre consolidado del admin, rango de fechas, exportable, marcar liquidado.
9. **C-08 + C-17** — subsistema de archivos (una sola vez para los dos) + registro ampliado con adjunto de
   matrícula + pantalla `/perfil` con foto. De C-08 arranca lo que no depende del cliente (DNI, celular, CUIT,
   upload); los nombres finales de matrícula y condición fiscal se completan cuando respondan.

### Ola 3 — Catálogo enriquecido *(**DESBLOQUEADA** 2026-08-03: llegó el Excel)*
10. **C-12** — ingesta del maestro (parser + match por SKU + botón en Integraciones).
11. **C-11** — filtros de categoría, subcategoría, laboratorio, presentación.
12. **C-10** — tags con ranking en el buscador.
13. **C-13** — filtro de rubro producto terminado (`IdRubro=144331`).

> **El plan detallado de esta ola vive ahora en `07-maestro-articulos-y-catalogo.md`** (pasos 3.A–3.F),
> escrito contra el Excel real ya medido. Ahí también entran los 3 ajustes nuevos del sync que pidió Gon
> en el mismo mail (código de barras, `Tipo=Producto`, rubro permitido) y las preguntas que quedan abiertas
> — sobre todo **si los combos se recetan o no** (209 productos publicados en juego).
> **Presentación y principio activo no existen en el maestro**: los cubren tags + subcategoría.

### Ola 4 — TiendaNube real *(ya estaba en Fase 2)*
14. App en el Partner Portal, cupones reales, webhook `order/paid`, y recién ahí se valida C-03 de punta a punta.

### Backlog explícito (v1.2, acordado en la call)
- Stock por depósito vía `getStockBySKU` (C-15).
- Imágenes de producto (C-16).
- Campos extra del paciente a pedido de la nutricionista tester (`18:05`).
