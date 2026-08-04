# DIARIO — Bitácora compartida de Claudes

> **Qué es esto.** Una bitácora append-only donde cada Claude (el de Santi y el de Fran) deja registro de
> **qué hizo**, **por qué**, **qué errores se encontraron**, y **qué debería saber el otro**. Sirve para que
> la próxima sesión (cualquiera de los dos) arranque con contexto real de lo que ya pasó.
>
> **Regla de uso para el Claude que esté activo:**
> 1. Al **arrancar sesión**: leer las últimas ~10 entradas (las más recientes arriba).
> 2. Al **cerrar una tarea no trivial** (feature, bug-fix, decisión arquitectónica, cambio en infra, fix de
>    build, etc.): agregar una entrada siguiendo el formato de abajo.
> 3. **No editar entradas viejas.** Si algo cambió, agregar entrada nueva con "corrección" o "actualización".
> 4. Ordenar por fecha **descendente** (lo más nuevo arriba).
>
> **No es para:** changelogs de usuario, release notes, ni PR descriptions. Eso va a otros archivos.
>
> **Cuándo archivar.** Cuando el archivo supere ~300 entradas, moverlas a `DIARIO-archivo-YYYY-Qn.md` y dejar este vacío.

---

## Formato de entrada

```markdown
## YYYY-MM-DD — <autor: Santi|Fran> — <área: backend|frontend|infra|db|auth|integraciones|...>
**Qué:** <qué cambió en 1-2 líneas>
**Por qué:** <motivación; requisito / bug / decisión>
**Problemas:** <errores que aparecieron y cómo se resolvieron; omitir si no hubo>
**Impacto para el otro:** <qué necesita saber la otra persona; omitir si no aplica>
**Refs:** <commits, archivos clave, PRs; opcional>
```

---

## Entradas

## 2026-08-04 — Santi — frontend (pantallas sin scroll: emisión, navbar/footer fijos, detalle en modal)
**Qué:** Cinco ajustes de layout salidos de usar la app. `tsc`/`oxlint`/`build` verdes.

- **Emitir receta rediseñada como panel de trabajo de alto fijo.** Era una página larga: al agregar
  el tercer producto había que scrollear para encontrar el botón de emitir, y el buscador quedaba
  arriba fuera de pantalla. Ahora la pantalla ocupa exactamente el viewport disponible y **no
  scrollea**: scrollean por dentro las dos listas que pueden crecer sin límite (resultados del
  buscador e items de la receta). El buscador, los totales y el botón quedan siempre a la vista.
  El paciente pasó de ocupar una tarjeta entera —para mostrar un solo dato— a una píldora en la
  barra de título, y ese alto se lo quedó el buscador. Abajo de 980px vuelve a ser una página que
  scrollea: no hay dos columnas que sostener.
- **Navbar fijo.** Estaba en `position: sticky` y no funcionaba: hay un `html, body { overflow-x:
  hidden }` (para que nada desborde a lo ancho) que crea un contexto de scroll y **anula el
  sticky**. Pasado a `fixed`, que se ancla al viewport y no se ve afectado. El alto de las dos
  barras vive ahora en `--navbar-h`/`--footer-h` y el shell las reserva con padding.
- **Footer reducido a la barra fija** (marca + copyright + Simple Apps). Los links que tenía arriba
  se eliminaron por dos razones: para la nutricionista duplicaban la navbar, y **para el admin
  apuntaban a rutas que no puede ver** (Panel/Recetas/Pacientes/Cierre son sólo de nutricionista
  por C-07) — o sea que lo mandaban a un 403 o lo rebotaban al home. Era un bug, no sólo ruido.
- **Detalle de producto: de fila expandible a modal.** La fila expandible empujaba todas las de
  abajo, la tabla saltaba y se perdía de vista lo que se venía leyendo.
- **Ficha de nutricionista sin scroll**: estado y email en una línea arriba, datos en grilla densa,
  los dos porcentajes y su aclaración en una sola fila, y las acciones de cuenta pasaron de tarjetas
  con título+descripción a una línea cada una (botón + aclaración al lado).

**Pregunta del usuario — de dónde salen las imágenes:** del **maestro**, columna
`LINK IMAGEN TIENDA NUBE`. Son URLs al CDN de TiendaNube (`dcdn-us.mitiendanube.com`): guardamos el
link, no el archivo, así que no cuestan storage pero dependen de que TBC no las mueva. Cobertura
real hoy: **173 de 699 recetables (25 %)** — por eso la fila de producto tiene que verse bien sin
imagen.
**Refs:** `pages/EmitirReceta.tsx`, `pages/CatalogoAdmin.tsx`, `components/layout/Footer.tsx`,
`components/nutricionista/ParametrosModal.tsx`, `components/receta/ProductoBuscador.tsx`, `index.css`.

## 2026-08-04 — Santi — frontend+backend (detalle expandible del catálogo del admin, con los tags)
**Qué:** Pedido: "traer los tags del maestro y que filtren en la búsqueda, y poder expandir el
producto viendo más detalles con los tags como burbujas".

**La mitad ya estaba hecha desde la Ola 3 y lo verifiqué antes de tocar nada:** el importador ya lee
la columna TAGS y la aplica (**8447 tags sobre 539 productos** en la DB actual), y el buscador ya
filtra por ellos — `q=veganos` devuelve 15 resultados, `q=fertilidad` 6, `q=magnesio` 54. No hacía
falta cambiar el script.

**Lo que sí faltaba, y es lo que se hizo:** la pantalla `/catalogo` del admin listaba productos pero
no mostraba tags ni dejaba ver el detalle. Ahora cada fila se expande (chevron + click en la fila) y
abajo aparece: imagen, los 9 datos de las dos fuentes (código de barras, taxonomía, laboratorio,
rubro y tipo del ERP, estado en el ERP, fechas de sync e import), la descripción web y **los tags
como burbujas**. Sólo lectura, como se pidió: el catálogo lo escriben el sync y el import, no esta
pantalla. En el buscador de recetas los tags siguen siendo clickeables (filtran) — son dos usos
distintos y por eso son dos estilos distintos (`.burbuja` vs `.tag-chip`).

Cuando un producto no tiene tags el detalle explica **por qué**, que es lo accionable: "el maestro lo
tocó pero no le cargó tags" si matcheó, o "no está en el maestro: sin tags no se lo encuentra por
palabra clave" si no. La columna Tags de la tabla muestra el conteo, así se ve de un vistazo dónde
falta carga.

**Backend:** `AdminProductoResponse` suma `tipoErp`, `activoErp` y `rubro` — no viajan al emisor
(a la nutricionista no le dicen nada) pero son el contexto de por qué un producto entró o quedó
afuera del catálogo recetable. `mvn test` 149 verde; front `tsc`/`oxlint`/`build` verdes.
**Refs:** `pages/CatalogoAdmin.tsx`, `components/ui/Icon.tsx` (ícono `chevron`), `index.css`,
`AdminProductoResponse`, `ProductoService.toAdminResponse`.

## 2026-08-04 — Santi — frontend (diálogos propios, ficha del admin y arreglos de la tanda anterior)
**Qué:** Cuatro cosas que salieron de mirar la app en vivo. `tsc`/`oxlint`/`build` verdes.

- **Fuera todos los `window.confirm` / `window.prompt`.** Eran 9 en 5 archivos. Nuevo
  `components/ui/Dialog.tsx`: `DialogProvider` + `useDialog()` con `confirmar()` y `pedirTexto()`,
  ambos promesa-based para que el call site siga leyéndose igual que antes
  (`if (!(await confirmar({...}))) return;`). Más allá de la estética, el diálogo del browser no
  deja explicar nada: en un borrado irreversible hace falta decir qué se pierde y qué alternativa
  hay, y eso no entra en una línea de texto plano. Ahora el botón que confirma **nombra la acción**
  ("Borrar definitivamente") en vez de decir "Aceptar", y va en rojo si es destructiva.
- **Ficha de nutricionista rediseñada.** Los botones estaban todos en una fila de tamaño parecido:
  guardar convivía con borrar-para-siempre. Quedó partida en dos zonas: arriba la ficha y los
  porcentajes con su botón primario abajo a la derecha, y separada una zona "Cuenta" donde cada
  acción (contraseña / desactivar / borrar) es una fila con su explicación al lado — sin eso,
  "Desactivar" y "Borrar" son dos botones parecidos y la diferencia entre ellos sólo se descubre
  apretando.
- **Datos de "Mi perfil" descomprimidos**: pasaron de grilla a una fila por dato con separadores.
  En grilla, la etiqueta de un campo y el valor de otro quedaban pegados y se leía como texto corrido.
- **`/catalogo` (Productos del admin) redirigía a `/nutricionistas`.** El código de la ruta y el
  guard estaban bien: era el **cache de Vite**. Edité `App.tsx` agregando el import de
  `CatalogoAdmin` *antes* de crear el archivo; Vite cachea el fallo de resolución y desde ahí la
  ruta no existía en el bundle, así que caía en el fallback `path="*"` → `/` → Login con sesión →
  home del rol → `/nutricionistas`, que es exactamente el síntoma. Se resolvió reiniciando el dev
  server con `node_modules/.vite` borrado. **Al crear un archivo nuevo, crearlo antes de importarlo.**

**De paso:** `CierreConsolidado` seguía usando la clase `filtros__date`, que había sido reemplazada
por `filtros__campo` al alinear los filtros de recetas — sus dos fechas estaban sin estilo.
**Refs:** `components/ui/Dialog.tsx` (nuevo), `components/nutricionista/ParametrosModal.tsx`,
`pages/{Perfil,Pacientes,CierreConsolidado}.tsx`, `components/receta/RecetaDetalle.tsx`, `index.css`.

## 2026-08-04 — Santi — backend+frontend+db (11 cambios pedidos: parámetros, cuentas, catálogo admin y UI)
**Qué:** Tanda grande de cambios pedidos por el usuario. `mvn verify` **149 unit + 1 IT**; front `tsc`/`oxlint`/
`build` verdes. Dos migraciones (`V011`, `V012`). Verificado e2e contra el stack (back `:8088`).

**🔎 El "bug" de la contraseña no era un bug.** Se reportó que un usuario dado de alta no podía loguear
"porque no le tomaba la contraseña". Reproducido de punta a punta: registro → aprobar → login **funciona**, y
el usuario en cuestión (`santiscally@gmail.com`) tiene su credencial `password` en Keycloak, `enabled=true` y
`APROBADA`. Lo que sí muestra Keycloak es **3 intentos fallidos** desde la IP del host — con `failureFactor=30`
no llegó a bloquearse. Quedan dos explicaciones: un typo, o haber probado **antes de aprobar** (ahí Keycloak
responde `Account disabled`, que ya está traducido pero no aclara que la contraseña estaba bien).
**Lo que sí era un agujero real: no existía ninguna forma de recuperar el acceso** — no hay "olvidé mi
contraseña" ni reset por admin. Quien se equivocaba quedaba afuera para siempre. Eso es lo que se construyó.

**Backend:**
- **`V011` — se elimina la configuración global de %.** Convivían un global (`configuracion_sistema`) y un
  override por nutricionista donde `NULL` significaba "usá el global": el mismo dato en dos lugares y dos
  formas de leerlo, y cualquier lectura que se salteara el resolutor devolvía un número distinto al de la
  emisión. Ahora cada nutricionista tiene los suyos, obligatorios. La migración **hereda el global vigente**
  (no un literal 15/10: si el admin lo había cambiado, el valor que estaba aplicando es el suyo) y verificado
  en la DB real: `ana.test` conservó su 30/8 y el resto quedó en 15/10. Las recetas ya emitidas no se tocan
  (los % están snapshoteados desde C-01). Se borró el módulo `configuracion` entero y la pantalla del front;
  `ParametrosNegocioService` se mudó a `modules/nutricionista/`. El descuento ahora viaja en `GET /me`.
- **`V012` + gestión de cuentas del admin.** Columna `activo` (espejo del `enabled` de Keycloak, para que la
  bandeja muestre 20 filas sin hacerle 20 requests a Keycloak) y cuatro acciones:
  `desactivar`/`reactivar` (reversibles, conservan todo), `DELETE` (borra usuario + fila + pacientes +
  archivos) y `POST /password` (reset). **Borrar se niega con 409 si emitió recetas**: esas recetas alimentan
  los cierres y borrar a su autora dejaría plata contabilizada sin nadie a quien atribuírsela; el mensaje
  manda a desactivar. El orden importa — primero Keycloak, después la fila local: al revés quedaría un
  usuario capaz de loguearse sin perfil (hay test).
- **`PUT /perfil/password`.** Exige la contraseña actual y la verifica contra Keycloak por ROPC, porque la
  Admin API pisa credenciales sin conocer la anterior: sin ese chequeo, una sesión abierta y olvidada
  alcanzaría para quedarse con la cuenta. El reset del admin además **limpia los intentos fallidos** — si
  alguien llegó a pedirlo es probable que haya reintentado hasta frenarse contra la protección de fuerza bruta,
  y la contraseña nueva no le serviría hasta que expirara el bloqueo.
- **La nutricionista deja de ver facturación** (extiende C-02, pedido del usuario a mitad de la tanda): fuera
  `ordenTotal` de `RecetaResponse.Conversion`, `ventasGeneradas*` del resumen, del cierre mensual y de las
  estadísticas. El detalle de conversión ahora dice sólo la comisión. **El admin lo conserva** en el cierre
  consolidado: es con lo que liquida. Se borró `sumVentasEntre`, que quedó sin uso.
- **`GET /admin/productos` + `/resumen`.** El catálogo con los despublicados incluidos y el motivo resuelto en
  castellano (`PublicacionPolicy.motivoNoPublicable`). Contra el catálogo real: **2267 productos, 699
  recetables, 104 sin match del maestro, 484 bloqueados**.
- **`/productos/filtros` ahora trae `precioMin`/`precioMax`** reales (4.011 – 1.421.999) para los extremos del
  slider: sin eso el front tendría que inventar un tope.

**Frontend:** pantalla `/catalogo` para el admin con tarjetas y filtro "sin match del maestro"; buscador con
los filtros **plegados** detrás de un botón (eran 8 controles siempre a la vista tapando la lista) + chips de
lo aplicado + **slider de precio de doble pulgar**; filtros de recetas alineados (todos con label y 40px de
alto — antes las fechas iban en un label de dos líneas y quedaban más bajas); `/perfil` en dos columnas con
form de contraseña; notas y fecha de nacimiento **editables** en pacientes y visibles en el listado; footer
reducido con la barra de copyright + Simple Apps **fija** al pie; modal con alto acotado al viewport y scroll
en el cuerpo (el "Más info" de un producto con descripción larga se cortaba y el botón de cerrar quedaba
fuera de pantalla).

**Problemas:**
1. **Cualquier ruta inexistente devolvía 500** ("Error interno") en vez de 404 — el catch-all del
   `GlobalExceptionHandler` se comía `NoResourceFoundException`. Preexistente, pero salta ahora que
   `/configuracion` dejó de existir y un front desactualizado lo va a seguir pegando. Arreglado.
2. **`Object[]` de un query con dos agregados viene anidado** (`Object[]{Object[]{min,max}}`) según el caso;
   hay que desanidar antes de leerlo o el rango de precios sale null.
3. **El `curl` de Git Bash manda los acentos en cp1252** y el backend responde 400 "JSON malformado". Me hizo
   creer que el PUT de pacientes ignoraba las notas. Con el body en un archivo UTF-8 anda: verificado el
   round-trip completo. **Para probar endpoints con texto en castellano: `--data-binary @archivo`, nunca `-d`
   inline.**

**Verificado e2e:** migraciones aplicadas y `configuracion_sistema` eliminada; desactivar → `Account disabled`
→ reactivar → login OK; reset de contraseña → login con la nueva; borrado de una nutricionista sin recetas
(y su login pasa a `Invalid user credentials`); **409 al intentar borrar una con 6 recetas**; cambio de
contraseña propio (rechaza la actual incorrecta con 409, acepta la correcta con 204); notas de paciente
round-trip; `/configuracion` → 404.

**Impacto para el otro (Fran):** contrato con **cambios que rompen** — `RecetaResponse.Conversion` pierde
`ordenTotal`; `DashboardResumen` pierde `ventasGeneradasMesActual`; `CierreMensual` pierde `ventasGeneradas` y
`Detalle.ordenTotal`; `EstadisticasMes` pierde `ventasGeneradas`; `NutricionistaAdmin` pierde
`descuentoPctEfectivo`/`comisionPctEfectiva` (ahora `descuentoPct`/`comisionPct` son obligatorios) y suma
`activo`; `GET /configuracion` **ya no existe** (el descuento sale de `/me`). Todo el front del repo ya quedó
actualizado.
**Refs:** `V011__parametros_solo_por_nutricionista.sql`, `V012__nutricionista_activo.sql`,
`AdminNutricionistaService` (+ test nuevo, 11 casos), `PerfilController`, `KeycloakAdminClient`,
`ProductoService.searchAdmin`, `PublicacionPolicy.motivoNoPublicable`, `frontend/src/pages/CatalogoAdmin.tsx`,
`components/receta/RangoPrecio.tsx`, `components/ui/Modal.tsx`, `index.css`.

## 2026-08-04 — Santi — backend+frontend+db (2.4: WhatsApp por link `wa.me`, se saca la Cloud API)
**Qué:** Ejecutada la tarea 2.4, que estaba decidida desde el 2026-07-28 y anotada sin tocar código. El envío
por WhatsApp pasa a ser **manual**: el backend devuelve `waMeUrl` en el `RecetaResponse` y la nutricionista
toca un botón que le abre el chat con la paciente con el mensaje ya escrito. `mvn verify` **147 unit + 1 IT**
(141 → +8 del builder, +1 de wiring, −3 del test de la Cloud API que se borró); el IT confirma además que las
10 migraciones aplican limpias sobre una Postgres nueva. Front `tsc`/`oxlint`/`build` verdes.

**Por qué:** el envío automático exigía WABA, número de empresa verificado y template aprobado por Meta —
trámites del cliente, no trabajo nuestro— más costo por conversación. El link no necesita nada de eso y el
mensaje sale del número que la paciente ya conoce.

**Lo que se sacó** (WhatsApp dejó de existir como integración, no sólo como adapter):
- `integrations/whatsapp/**` (port + `CloudApiWhatsAppSender` + stub) y su test.
- El valor `WHATSAPP` de `CanalNotificacion`, la rama del `NotificacionDispatcher` y el encolado en
  `NotificacionService`. **El único canal automático es el email.**
- El proveedor `whatsapp` de `GET /admin/integraciones/estado` (quedan 3), `IntegrationsProperties.WhatsApp`,
  el `Proveedor.WHATSAPP` del health registry y la config `WHATSAPP_*` de `application.yml`/compose/`.env.example`.
- **Migración `V010`**: borra las filas `canal='WHATSAPP'` y deja el CHECK en `('EMAIL')`.

**Decisiones que vale la pena registrar:**
1. **El borrado de las notificaciones WHATSAPP es físico**, contra la regla de soft-delete del proyecto. Una
   fila soft-deleted con un valor que el enum ya no tiene igual revienta cualquier lectura que no filtre por
   `deleted_at` (`findById`, `findAll`): la bomba queda armada esperando. Es una cola operativa, no un
   registro de negocio, y en stub nunca salió un mensaje. La receta, que es el dato real, no se toca.
2. **`waMeUrl` sólo viene si la receta está `PENDIENTE`** y el paciente tiene teléfono utilizable. Una
   ANULADA o VENCIDA daría un código muerto. El front decide mostrar el botón por la presencia del campo,
   sin repetir la regla de estados.
3. **Espacios como `%20`, no como `+`.** `URLEncoder` es form-encoding y manda `+`; algunos clientes de
   WhatsApp lo muestran literal en el mensaje. Hay un test que lo fija.
4. **El botón es la acción principal de la pantalla de éxito** (verde de marca), y "Emitir otra receta" bajó
   a secundaria: mandar el WhatsApp dejó de ser algo que hace el sistema y pasó a ser un paso que si la
   nutricionista se saltea, no ocurre. En el detalle de receta el botón convive con "Reenviar mail"
   (renombrado: reenviar ya no manda WhatsApp).

**Verificado e2e** contra el stack real (back `:8088`): `V010` aplicada (`success=t`), CHECK quedó en
`canal = 'EMAIL'`, 0 filas WHATSAPP; una receta PENDIENTE devuelve el link y las LIQUIDADAS no; el mensaje
decodificado sale correcto con acentos y emoji ("Hola Lucía! 🌱 … Código: *RX-HVUBRK* (válido hasta el
30/08/2026)"); emisión nueva → `waMeUrl` + una sola notificación EMAIL; el panel de integraciones devuelve
3 proveedores.

**Pendiente menor:** el mensaje no lleva link a la tienda (no hay URL del storefront configurada en ningún
lado; el mail tiene el mismo hueco). Se cierra en Fase 2 cuando esté la tienda real.

**Impacto para el otro (Fran):** `RecetaResponse` suma **`waMeUrl?: string | null`** (aditivo);
`RecetaNotificacion.canal` ya sólo puede ser `"EMAIL"`; `GET /admin/integraciones/estado` devuelve 3
proveedores. Los types espejo y las pantallas (`RecetaExito`, `RecetaDetalle`, `Integraciones`) ya quedaron
actualizados.
**Refs:** `modules/receta/service/WaMeLinkBuilder.java` (nuevo) + su test, `V010__notificaciones_solo_email.sql`,
`RecetaResponse`, `NotificacionService`/`Dispatcher`/`Templates`, `IntegrationsConfig`/`Properties`,
`IntegracionesEstadoService`, `frontend/src/components/receta/RecetaExito.tsx` + `RecetaDetalle.tsx`,
`03-integraciones-apis.md §4`.

## 2026-08-03 — Santi — frontend+infra (CORS del puerto 5174 + registro en una sola pantalla)
**Qué:** Dos cosas que salieron de probar la app en vivo. Front `tsc`/`oxlint`/`build` verdes.

- **CORS roto al levantar el front en `:5174`.** Login y registro fallaban con *"No 'Access-Control-Allow-Origin'
  header"*. Había que tocar **tres** lugares, no uno: (1) `app.cors.allowed-origins` del backend, (2) el
  **`.env` local**, que pisaba el default del compose —por eso el primer rebuild no cambió nada—, y (3) los
  **`redirectUris` del client `nutriapp-frontend` en Keycloak**, porque tiene `webOrigins: ["+"]`, o sea que
  los orígenes permitidos se derivan de los redirectUris: sin `http://localhost:5174/*` ahí, el `/token`
  responde sin header CORS por más que el backend esté bien.
  Quedaron los dos puertos permitidos (5173 y 5174) en `application.yml`, `docker-compose.yml`,
  `.env.example`, `.env` y el realm JSON, **y aplicado también al Keycloak vivo** por la Admin REST API
  (el JSON del realm solo se importa en un realm nuevo). Verificado con `curl -H "Origin: ..."`.
- **Registro en una sola pantalla.** Con C-08 el form pasó a 11 campos y en el card de 460px a dos columnas
  obligaba a scrollear. Ahora el card va a 720px y la grilla a **tres columnas**, con los campos reordenados
  para que las filas cierren completas (email ocupa dos celdas, el adjunto el ancho completo). Se agregó
  compactación por **alto de viewport** (`@media (max-height: 880px)`) en vez de dejar que aparezca scroll en
  notebooks, y breakpoints 3→2→1 columnas.
- **Alineación:** el `<select>` de condición fiscal usaba el padding base (`0.5rem`) y los inputs del auth
  `12px`, así que al lado quedaban de distinta altura; ahora inputs, selects y file inputs comparten caja.
  El `input[type=file]` tiene el botón nativo estilado como el resto.
- **Responsive del resto:** `overflow-x: hidden` global, la fila de producto (que ahora tiene 5 columnas por
  la miniatura y el "Más info") se apila en dos líneas abajo de 720px, los filtros del buscador pasan a ancho
  completo, y la grilla de integraciones a una columna.

**Pendiente:** verificación visual. Está todo verde por contrato pero no pude mirar las pantallas (no hay
herramientas de browser en esta sesión). Front en `:5174`, back en `:8088`.
**Impacto para el otro (Fran):** si levantás el front en un puerto distinto de 5173/5174, hay que agregarlo
en los tres lugares de arriba — está anotado en `.env.example`.
**Refs:** `frontend/src/pages/Registro.tsx`, `frontend/src/index.css`, `keycloak/realms/nutriapp-realm.json`,
`backend/src/main/resources/application.yml`, `docker-compose.yml`.

## 2026-08-03 — Santi — backend+db (maestro de artículos de TBC: C-10/C-11/C-12/C-13 + los 3 ajustes del mail de Gon)
**Qué:** Llegó el Excel maestro que faltaba desde la demo → **Ola 3 desbloqueada y hecha del lado backend**.
`mvn test` **141 unit** BUILD SUCCESS (+28). Migración `V009`. Análisis completo en
**`07-maestro-articulos-y-catalogo.md`** (documento nuevo, referenciado desde `CLAUDE.md` y desde el `06-`).

- **El archivo no se commitea.** De sus 126 columnas nutriapp usa 9; las otras traen costo, margen,
  comisión y precio por proveedor de 2225 artículos — la estructura de costos de TBC. Va al `.gitignore`
  igual que `presupuesto_nutriapp.pdf`. Y el importador **lee solo esas 9 y descarta el resto**: los costos
  nunca entran a la base.
- **C-12 importador** (`modules/producto/maestro/`): `POST /admin/productos/importar-maestro` multipart +
  `GET /admin/productos/maestro/estado`. Parser con `fastexcel-reader` (streaming, ~200 KB, contra los
  ~12 MB de deps de Apache POI para leer 9 columnas). **Busca las columnas por nombre, nunca por posición**:
  son 126 columnas de una planilla que el cliente edita a diario, y un parser posicional escribiría marcas
  en el campo de categoría sin que nadie se entere. Reporte con filas leídas/matcheadas/sin match/rechazadas.
- **Dos escritores, cero campos compartidos.** Contabilium es dueño de nombre/precio/stock/marca/rubro/
  tipo/código de barras; el maestro de departamento/categoría/subcategoría/laboratorio/descripción web/
  imagen/tags/bloqueo. Con esa partición **importar y sincronizar son conmutativos** — el admin no puede
  romper nada haciéndolo en el orden "equivocado". `publicado` dejó de ser un campo que alguien escribe y
  pasó a ser derivado (`PublicacionPolicy`, una sola definición para los dos procesos).
- **`categoria` cambió de dueño.** Guardaba el Rubro de Contabilium, que vale "Producto terminado" para el
  99,8 % del catálogo recetable: como filtro era decorativo. El rubro se mudó a su columna (`rubro`/`rubro_id`,
  ahora filtro de ingreso) y `categoria` pasó a ser la del maestro, con 23 valores reales.
- **C-10 buscador rankeado**: `q` matchea nombre + descripción + SKU + **código de barras** + **tags**, y
  ordena nombre → descripción → tag, que es literal lo que pidió Gon en la call (`29:15`). Los tags entran
  por `EXISTS` y no por `JOIN`: con JOIN, un producto con 20 tags que matchean salía 20 veces y rompía la
  paginación. **C-11 filtros** de departamento/categoría/subcategoría/laboratorio + `taxonomia` en cascada
  (142 subcategorías sueltas en un dropdown no las usa nadie).

**Problemas:**
1. **`Tipo` de Contabilium tiene TRES valores, no dos.** Gon pidió "solo quedarnos con Producto" pensando en
   sacar los servicios, pero el campo también vale `Combo` (209 artículos: los packs x2/x3 y los exhibidores
   de la línea ON-ROLL propia de TBC). Aplicado tal cual, **el catálogo recetable caía de 702 a 496 (−29 %)**.
   **Decidido con Santi: van los dos** (`CATALOGO_TIPOS_ERP=Producto,Combo`, que quedó como default en
   `application.yml`/compose/`.env.example`) → **699 recetables, 203 combos**. Igual hay que consultárselo a
   Gon; volver a su versión literal es cambiar el env var y re-sincronizar. Por eso `tipos-erp-permitidos`
   es una **lista** y no un valor único.
2. **Un `.xlsx` inválido salía como 500 "error interno"** en vez de un mensaje accionable: el `IOException`
   del zip se escapaba como `UncheckedIOException`. Lo encontró un test. Ahora 422 diciendo qué pasa.
   De paso quedó `UnprocessableException` + handler para archivos que el usuario puede arreglar.
3. **El default de multipart de Spring Boot es 1 MB** y el maestro pesa 1,5 MB: el import habría fallado
   siempre. Subido a 25 MB en el contenedor; el tope de negocio real lo pone `catalogo.import-max-bytes`.
4. **`mvn -q` me ocultó un BUILD FAILURE** al filtrar la salida con `Select-String`: di por compilado algo
   que no compilaba. Correr sin `-q` cuando se filtra el output.
5. **`RecetaFlowIT` estaba roto desde el 2026-07-28** (no por esto): tomaba `productoRepository.findAll().get(0)`,
   o sea el primer producto del seed de `V003`… que ese día se vació a propósito. Nadie corrió `mvn verify`
   desde entonces. Ahora el test **crea su propio producto** y no depende de ninguna migración de datos.
   `mvn verify` vuelve a estar verde: **141 unit + 1 IT**, y las 9 migraciones aplican limpias sobre una
   Postgres nueva (que es la validación real de `V009`, porque en la DB local corrió sobre datos existentes).

**Verificado e2e** contra el stack real y el archivo de verdad: import 2225 filas → **2163 aplicadas, 62 sin
match, 0 rechazadas** (coincide exacto con el análisis offline del Excel contra la DB), 26 despublicados por
`ESTADO=BLOQUEADO`, 8447 tags. Después corrí el sync de Contabilium y **no pisó un solo campo del maestro**.
Buscador: `q=magnesio` → 41 resultados con los de nombre arriba y los de solo-tag al final; `q=7798349830060`
→ ON-ROLL FLOW. Filtros: 4 departamentos / 14 categorías / 72 subcategorías / 35 laboratorios / 125 marcas.

**Hallazgo de alcance:** el presupuesto promete buscar "por principio activo, presentación, marca,
laboratorio". **Presentación no existe** en el maestro (lo más cercano tiene 12 % de cobertura) y **principio
activo tampoco**. Lo cubren los **tags**: el ejemplo que dio Gon en la call fue buscar "magnesio", y magnesio
está como tag en 39 artículos. Por eso C-10 se hizo con ranking y no como un OR suelto.

**Frontend (con autorización explícita del usuario, mismo criterio que C-06/C-07/C-09):** `tsc -b`, `oxlint`
y `vite build` verdes.
- **`MaestroImportCard`** en `/integraciones`, al lado del sync de Contabilium: examinar + importar + la
  última importación + el reporte. **Si hay filas sin match o rechazadas el toast sale como advertencia, no
  como éxito** — es el caso justo en el que un "importado correctamente" verde engaña. Los SKUs sin match se
  despliegan.
- **Buscador**: filtros nuevos de departamento/categoría/subcategoría/laboratorio **encadenados con el árbol
  de `/productos/filtros`** (sueltas, las subcategorías son 142 opciones y no las usa nadie), y cambiar un
  nivel limpia los de abajo. Placeholder del input ahora menciona el código de barras.
- **Miniatura + "Más info"**: la columna de la miniatura existe siempre, con placeholder cuando no hay imagen
  (solo el 24 % la tiene) para que las filas no queden desalineadas. El modal muestra imagen grande, SKU,
  código de barras, laboratorio, marca, categoría, el texto largo del maestro y los tags; **click en un tag
  filtra por ese tag**, que es la navegación "por propiedad" que el catálogo no tiene como campo.

**Verificación visual pendiente**: está todo verde por contrato (typecheck, lint, build, respuestas reales de
la API) pero nadie miró las pantallas. Front levantado en **:5174** (el 5173 lo ocupa imedba en esta máquina).

**Impacto para el otro (Fran):** **nada se rompe** — todo lo del contrato es aditivo. `ProductoResponse` suma
`codigoBarras`, `descripcionWeb`, `departamento`, `subcategoria`, `tags[]` (y `laboratorio` por fin tiene
datos); `ProductoFiltrosResponse` suma `departamentos`, `subcategorias`, `laboratorios` y `taxonomia`.
**Pero `categoria` cambió de significado**: antes traía "Producto terminado" para casi todo, ahora trae la
categoría real del maestro. `principioActivo` y `presentacion` siguen en el contrato pero son siempre `null`.
Toqué `frontend/`: `types/producto.ts`, `types/maestro.ts` (nuevo), `api/maestro.ts` (nuevo),
`components/admin/MaestroImportCard.tsx` (nuevo), `components/receta/ProductoBuscador.tsx`,
`pages/Integraciones.tsx`, `index.css`. Contrato actualizado en `05-api-endpoints.md`.
**Refs:** `V009__catalogo_maestro.sql`, `modules/producto/maestro/*`, `PublicacionPolicy`, `CatalogoProperties`,
`07-maestro-articulos-y-catalogo.md`.

## 2026-08-02 — Santi — backend+frontend (C-08 registro ampliado + C-17 foto de perfil) — CIERRA OLA 2
**Qué:** `mvn test` **113 unit** BUILD SUCCESS (+10); front `tsc`/`oxlint`/`build` verdes. Migración `V008`.

- **Subsistema de archivos** (lo comparten C-08 y C-17, por eso se hicieron juntos): tabla
  `nutricionista_archivos` + `ArchivoService`. **Los bytes van en la DB, no en un volumen**: son pocos y
  chicos (un PDF y una foto por persona) y así entran en el backup que ya existe (`scripts/backup-db.sh`)
  en vez de sumar una segunda cosa que acordarse de respaldar. Whitelist de content-type (no confiamos en
  la extensión), tope por tipo, y un archivo vigente por (nutricionista, tipo) — subir de nuevo reemplaza.
- **C-08 — registro ampliado.** `POST /registro` pasó a **multipart**: parte `datos` con el JSON y parte
  `matricula` con el PDF/imagen. Campos nuevos: **DNI** (con índice único parcial y guard de duplicados,
  que es justo para lo que Gon lo pidió), **CUIT** (normalizado sin guiones al guardar) y **condición
  fiscal**. El admin ve todo en la ficha y tiene **"Ver matrícula"**. El **CUIT ya sale en el exportable
  de C-06**, que era lo que le faltaba.
- **C-17 — foto de perfil.** `POST/DELETE /perfil/foto` + pantalla `/perfil`. La foto se **redimensiona en
  el server** a 256px de lado y se guarda en JPEG (una PNG de 400x400 y 220 KB quedó en 14 KB), y **viaja
  embebida como data URI en `/me`**: es un thumbnail de pocos KB y así el front pinta el avatar sin un
  segundo request autenticado ni manejar object URLs. Componente `Avatar` con fallback a iniciales.

**Problemas (dos, los dos reales):**
1. **`@Lob byte[]` no funciona contra `BYTEA` en Hibernate 6**: lo mapea a `oid` (large object) y tira
   *"column is of type bytea but expression is of type bigint"*. Va `@JdbcTypeCode(SqlTypes.VARBINARY)`.
2. **La compensación de Keycloak del registro no cubría fallas en el commit.** El error del punto 1 explotó
   al hacer flush **al cerrar la transacción**, o sea *fuera* del try/catch de `RegistroService` → el
   usuario de Keycloak quedó huérfano y bloqueando el email. Arreglado con `saveAndFlush` en
   `ArchivoService`: el INSERT revienta dentro del try y la compensación corre. **Vale como patrón**: en
   cualquier alta con compensación, forzar el flush antes de salir del bloque protegido.

**Verificado e2e** (script `verify_c08.sh`): registro multipart → PENDIENTE con DNI/CUIT/condición fiscal
guardados → el admin ve los datos y `tieneMatricula: true` → descarga el PDF **byte a byte idéntico** al
subido → una nutricionista pidiendo la matrícula de otra recibe **403** → foto 400x400 PNG de 220 KB queda
en JPEG de 14 KB → `/me` la trae como data URI → subir un PDF como foto da **409 con mensaje claro** →
CUIT presente en el consolidado. Validaciones del form: CUIT mal formado 400, DNI repetido 409.

**Nota de contrato para el front:** `api/client.ts` ahora detecta `FormData` y **no** le pisa el
`Content-Type` (si se lo seteás a mano, se rompe el boundary del multipart y el server no parsea nada).

**Impacto para el otro (Fran):** `Me` suma `foto` (data URI, opcional); `RegistroRequest` suma dni/cuit/
condicionFiscal y `registrar()` ahora pide el `File` de la matrícula; ruta `/perfil` nueva; componente
`Avatar` reemplaza al span de iniciales del navbar.

**Pendiente:** el campo `matricula` sigue llamándose así — Leo se llevó confirmar si va "matrícula
nacional" (call 42:34). Renombrarlo después es una migración de una línea.

**Refs:** `V008__nutricionista_datos_fiscales_y_archivos.sql`,
`modules/nutricionista/{entity/{NutricionistaArchivo,TipoArchivo},repository/NutricionistaArchivoRepository,service/ArchivoService,controller/PerfilController}`,
`modules/registro/**`, `common/web/MeController`, `frontend/src/{pages/{Perfil,Registro}.tsx,components/ui/Avatar.tsx,api/{perfil,registro,nutricionistas,client}.ts,types/{registro,session,nutricionista,cierre}.ts}`.

## 2026-08-02 — Santi — backend+frontend (C-06: cierre consolidado del admin con exportable)
**Qué:** Cierra el circuito de plata: conversión → comisión → liquidación → registro de que se pagó.
`mvn test` **103 unit** BUILD SUCCESS (+6); front `tsc`/`oxlint` verdes.

- **Backend:** `GET /api/v1/admin/liquidaciones/consolidado?desde&hasta` (`admin:manage`), una fila por
  nutricionista con actividad: recetas convertidas, facturado, comisión, y **cuánto queda impago** con los
  **ids de las recetas pendientes** para poder liquidar de un click contra el endpoint que ya existía.
  `CierreConsolidadoService` + `findConvertidasEntreTodas` (mismas reglas que el cierre individual: ventana por
  `ordenPaidAt` y cuenta APLICADA+LIQUIDADA).
  - **Rango configurable**, no mes calendario (Leo, 55:49). Fechas **inclusive de punta a punta** en hora
    argentina — "del 1 al 31" incluye todo el 31, que es donde este tipo de reportes suele perder un día.
  - **Sin paginar a propósito:** es una fila por nutricionista y el exportable tiene que salir completo, no la
    página que se esté mirando.
  - Guards: 409 si el rango está invertido, si falta una fecha o si supera 366 días (que nadie barra años de
    recetas de una); 403 para la nutricionista.
  - Ordena por **comisión pendiente descendente**: arriba a quien más hay que pagarle.
- **Frontend:** `/cierres` (solo admin) — rango con default al mes en curso, 4 tiles (convertidas, facturado,
  comisión, **a pagar**), tabla con botón "Liquidar N" por fila y confirmación que dice el monto, y
  **"Exportar CSV"**. Ícono `download` nuevo en el set.
- **El CSV está pensado para que Excel en español lo abra bien de una** (`lib/csv.ts`): separador `;` (con
  locale es-AR, la coma mete todo en una columna), **BOM UTF-8** (si no, se rompen acentos y ñ) y decimales con
  coma. Se genera en el front con los datos ya cargados: no vuelve a pegarle al backend y, como el endpoint no
  pagina, sale completo.

**Falta la columna CUIT** que Gon pidió explícitamente para el exportable (57:02): el campo no existe todavía
en `nutricionistas` — entra con **C-08**. Está anotado en el javadoc del DTO para que no se pierda.

**Verificado e2e:** receta nueva convertida a $8.000 → el consolidado muestra 3 recetas / $30.500 facturado /
$3.300 de comisión con **$2.050 pendientes en 2 recetas** → liquidar desde ahí devuelve "2 liquidadas por
$2.050" → el consolidado **mantiene el histórico** (3 recetas, $3.300) pero baja el pendiente a **0** →
reintento idempotente ("ya estaba liquidada"). Guards: 409 rango invertido, 403 como nutricionista.

**Impacto para el otro (Fran):** ruta `/cierres` + `types/cierre.ts` + `api/cierres.ts` + helper `lib/csv.ts`
reutilizable para cualquier otro exportable.

**Refs:** `modules/admin/{service/CierreConsolidadoService,dto/CierreConsolidadoResponse,controller/AdminLiquidacionController}`,
`modules/receta/repository/RecetaRepository`, `frontend/src/{pages/CierreConsolidado.tsx,api/cierres.ts,types/cierre.ts,lib/csv.ts,components/ui/Icon.tsx,App.tsx,components/layout/AppLayout.tsx}`.

## 2026-08-02 — Santi — frontend (C-09: bandeja de nutricionistas + mensajes de login traducidos)
**Qué:** Arranca la Ola 2. `tsc` + `oxlint` + `build` verdes. Backend sin cambios (los 4 endpoints ya existían).

- **C-09 — bandeja de nutricionistas** (`/nutricionistas`, sólo admin). Tres tabs: **Solicitudes pendientes /
  Aceptadas / Rechazadas** (la tercera no la pidieron, pero sin ella una rechazada desaparece de la vista y no
  hay forma de ver por qué se rechazó). La tabla muestra, por cada una, si el % es **override propio o el
  global** — el admin necesita ver cuál rige sin abrir la ficha. Desde la ficha (modal) se aprueba, se rechaza
  con motivo y se setean los % de C-01 en el mismo gesto: **vacío = usa el global**, así no hay que copiar el
  valor global en cada fila. Archivos nuevos: `types/nutricionista.ts`, `api/nutricionistas.ts`,
  `components/nutricionista/ParametrosModal.tsx`, `pages/Nutricionistas.tsx`, estilos `.tabs/.tab`.
- **La casa del admin pasó a ser `/nutricionistas`** (era `/configuracion`, interino mientras esto no existía):
  `lib/home.ts`, el brand de la navbar y el primer ítem del nav.
- **Mensajes de login traducidos** (era el ítem #1 de la auditoría de la pantalla de login, y lo destapó otra
  vez la verificación de este flujo): `lib/auth.ts` mapea `Account disabled` → *"Tu cuenta todavía no está
  habilitada: el administrador tiene que aprobar tu solicitud"*, más `Account temporarily disabled` (el realm
  tiene brute-force ON) e `Invalid client credentials`. Y **el error de red** ya no se ve como `Failed to
  fetch`: `fetch` sólo rechaza por red/CORS, así que se envuelve y sale *"No pudimos conectarnos con el
  servidor"*.

**Verificado e2e contra el stack real** (el flujo completo, no sólo las pantallas): registro público de
`ana.test@nutriapp.dev` → queda **PENDIENTE** → el login le da **"Account disabled"** (ahora traducido) →
aparece en la tab de pendientes con los % efectivos 15/10 heredados del global → el admin le setea **30% / 8%**
y la aprueba → queda **APROBADA con sus % propios** → **ahora sí puede loguearse**. Los contadores de las tabs
se mueven bien (pendientes 0, aprobadas 3).

**Dato de entorno:** quedó `ana.test@nutriapp.dev` / `test1234` en la DB y en Keycloak, creada por esta
verificación. Es una nutricionista APROBADA con 30%/8% — sirve para probar C-01 con dos perfiles distintos.
Si molesta para una demo, se borra de Keycloak + `nutricionistas`.

**Impacto para el otro (Fran):** ruta y pantalla nuevas + `lib/home.ts` decide el landing por rol. Si agregás
pantallas de admin, sumalas a `NAV_ADMIN` en `AppLayout` (no al array viejo, que ahora es `NAV_NUTRI`).

**Refs:** `frontend/src/{pages/Nutricionistas.tsx,components/nutricionista/ParametrosModal.tsx,api/nutricionistas.ts,types/nutricionista.ts,lib/{home,auth}.ts,App.tsx,components/layout/AppLayout.tsx,index.css}`.

## 2026-08-02 — Santi — backend+frontend (cierra Ola 1: C-07 admin sin recetas, C-02 sin precios)
**Qué:** Con Fran todavía de vacaciones, el usuario autorizó tocar `frontend/`. Cierra la Ola 1 del plan
`06-cambios-post-demo-2026-07-31.md`. Backend `mvn test` 97 unit BUILD SUCCESS; front `tsc` + `oxlint` + `build` verdes.

- **C-07 — el admin ya no emite recetas, de verdad.** No es esconder ítems del menú: el rol realm **ADMIN
  dejó de ser composite de `recetas:*`, `pacientes:*`, `productos:read` y `dashboard:read`** — queda sólo
  `admin:manage`. Aplicado en el `nutriapp-realm.json` **y** con `kcadm` sobre el Keycloak ya importado (si no,
  el cambio no entra hasta un re-import; misma lección que el service-account). Verificado: con token de admin,
  `/recetas`, `/pacientes`, `/dashboard/resumen` y `/productos` dan **403**, y `/admin/*` + `/me` siguen 200.
  La nutricionista quedó intacta.
  - Front: `NAV_ADMIN` (Configuración + Integraciones) vs `NAV_NUTRI`, CTA "Nueva receta" oculto para admin,
    guard `RequireRol` por ruta y `homeDe()` en `lib/home.ts` — cada rol arranca en su pantalla y el login
    redirige según rol (`login()` de `AuthContext` ahora devuelve el `Me` para poder decidir sin estado stale).
  - **Interino a mirar:** el admin queda con sólo dos pantallas y su "casa" es `/configuracion`, porque el
    cierre consolidado (C-06) y la bandeja de nutricionistas (C-09) todavía no existen en el front. Cuando
    esté C-09, la casa del admin debería pasar a ser la bandeja.
- **C-02 — precios sólo en la pantalla de emisión.** Backend: `RecetaResponse.Item` **ya no expone
  `precioLista`** (el snapshot se sigue guardando en `receta_items` para auditoría, pero no sale por la API).
  Front: sin importes en el detalle de receta, en la pantalla de éxito ni en la tabla del dashboard. En el
  dashboard la columna "Total" (que era una estimación con el precio de Contabilium) pasó a **"Venta"** con el
  monto real de TiendaNube, y "—" mientras no convierta. Se mantienen precios en el buscador y el carrito de
  emisión, con leyenda nueva: *"Valores aproximados. El precio final lo define la tienda…"* y el total pasó a
  llamarse **"Total estimado"**.
  - **Decisión discutible, marcada a propósito:** saqué los precios también de `RecetaExito` (la pantalla
    inmediatamente posterior a emitir). Se puede leer como parte de la emisión, pero Leo fue tajante con que no
    quede histórico con precios (53:35) y ahí ya la receta existe. Fácil de revertir si Gon lo pide.
  - **Lo que NO saqué:** el `producto` anidado del item sigue trayendo su `precio` **actual de catálogo**. No es
    el snapshot histórico y la nutricionista lo ve igual en el buscador, así que no contradice la regla.
- **C-05 en el front:** `EstadoReceta` suma `"LIQUIDADA"` (filtro del listado + badge propio, verde sólido), y
  el detalle muestra "Comisión liquidada el …" / "Comisión pendiente de liquidación".

**Pendiente de verificación:** todo lo anterior está verificado por contrato (API + typecheck + build), **no
visualmente**. Falta abrir las pantallas con los dos usuarios y mirar. Stack arriba: back `:8088`, front `:5173`.

**Impacto para el otro (Fran):** el contrato de `RecetaResponse.Item` **perdió** `precioLista` — cualquier
cálculo del front que dependiera de él ya no compila (revisé y ajusté los tres lugares que lo usaban).
`EstadoReceta` tiene un quinto valor. `AuthContext.login()` ahora devuelve `Promise<Me>` en vez de `Promise<void>`.

**Refs:** `keycloak/realms/nutriapp-realm.json`, `modules/receta/{dto/RecetaResponse,service/RecetaService}`,
`frontend/src/{App.tsx,components/layout/{AppLayout,RequireRol}.tsx,lib/home.ts,auth/AuthContext.tsx,pages/{Login,Dashboard,EmitirReceta,Recetas}.tsx,components/receta/{RecetaDetalle,RecetaExito}.tsx,types/receta.ts,index.css}`.

## 2026-08-01 — Santi — backend (Ola 1 post-demo: C-05 liquidación, C-04 cierre por fecha de pago, C-14 inactivos)
**Qué:** Primeros tres cambios del plan `06-cambios-post-demo-2026-07-31.md`. `mvn test` = **91 unit, BUILD SUCCESS**
(84 previos + 7 nuevos).
- **C-05 — estado terminal `LIQUIDADA`.** Migración `V006__receta_liquidada.sql` (nuevo valor en el CHECK de
  `estado` + columna `liquidada_at` + índice `(nutricionista_id, orden_paid_at)`). `EstadoReceta.LIQUIDADA` con
  helper `esConvertida()`. `LiquidacionService` (idempotente: lo ya liquidado se omite sin pisar la fecha) +
  `POST /api/v1/admin/liquidaciones` (`admin:manage`). La respuesta lleva `omitidas[]` con el **motivo** de cada
  receta que no se pudo liquidar — el admin tiene que ver qué quedó afuera, no un conteo mudo.
- **C-04 — el cierre agrupa por fecha de pago en TiendaNube.** Las agregaciones del dashboard y del cierre pasaron
  de ventanear por `aplicadaAt` a `ordenPaidAt`. En los datos actuales da igual (`aplicar()` ya seteaba
  `aplicadaAt = paidAt`), pero deja la regla explícita en vez de depender de esa coincidencia.
- **C-03 — verificado, ya estaba bien**: la comisión sale de `order.total()` (el total real de TiendaNube), nunca
  del precio de Contabilium. No hizo falta tocar nada.
- **C-14 — no se recetan inactivos de Contabilium.** `ProductoSyncService` ahora cruza el precio con el `Estado`
  del ERP. Defensivo a propósito: estado desconocido o nulo → se asume activo (preferimos publicar de más antes
  que vaciar el catálogo si Contabilium cambia el vocabulario).
- **Decisión de diseño (C-05):** las queries de cierre cuentan `APLICADA` **e** `LIQUIDADA`. Liquidar es haberle
  pagado a la nutricionista, no deshace la conversión: los cierres históricos tienen que seguir mostrando la
  receta, con `liquidadaAt` como marca de "ya cobraste esto". Lo que filtra sólo `APLICADA` es `findLiquidables`,
  que alimenta el cierre consolidado del admin (C-06, pendiente).

**Problemas (2 bugs preexistentes del working tree sin commitear, ninguno introducido por estos cambios):**
1. **NPE que tumbaba la sync entera de catálogo.** `ProductoSyncService.aplicar()` hacía
   `lk.rubros().get(c.idRubro())` sin chequear null, y el lookup puede ser un `Map` **inmutable** —
   `RubrosLookup.vacio()` es el fallback de `HttpContabiliumClient:106` cuando falla `/rubros`. `Map.of().get(null)`
   tira NPE, así que **un solo concepto sin rubro mataba el sync completo en live**. Arreglado chequeando los ids
   antes del lookup. Lo destaparon 5 tests que venían rotos en el working tree.
2. **`ProductoSyncServiceTest.sync_live_existenteSinCambios` desactualizado**: su fixture usaba precio $50, que
   bajo la regla de "precio irrisorio (<$100) = producto de baja" (trabajo del 28/07) despublica el producto y por
   lo tanto cuenta como cambio. Subido a $500, que es lo que el test quiso decir siempre.

**Impacto para el otro (Fran):** cambió el contrato en tres puntos, hay que espejar los types:
`RecetaResponse.Conversion` suma `liquidadaAt` (nullable), `CierreMensualResponse.Detalle` suma `liquidadaAt`
(nullable), y `estado` de receta ahora puede venir `"LIQUIDADA"` — el `EstadoReceta` del front tiene 4 valores y
necesita el quinto, y el filtro del listado debería ofrecerlo.

**Verificado e2e contra el stack real** (backend :8088, migración V006 aplicada según `flyway_schema_history`):
emitir → simular orden pagada ($12.500) → **APLICADA** con comisión 10% = $1.250 → liquidar como admin →
**LIQUIDADA** con `liquidadaAt` → reintento devuelve `liquidadas: 0, motivo: "ya estaba liquidada"` →
la receta **sigue apareciendo en el cierre mensual** de la nutricionista con su marca de pago →
`POST /admin/liquidaciones` con rol NUTRICIONISTA da **403**.

**C-01 también hecho (mismo día): % de descuento y comisión por nutricionista.** Migración `V007` (dos columnas
nullable con CHECK 0–100 en `nutricionistas`; **NULL = usá el global**, así no hay que backfillear ni duplicar la
config global en cada fila). `ParametrosNegocioService` es el **único** punto donde se resuelve override→global:
`RecetaService.emitir` (descuento) y el webhook (comisión) ya no leen `ConfiguracionService` directo — si alguien
vuelve a hacerlo se saltea el override y los cálculos quedan inconsistentes entre emisión y conversión.
`PUT /api/v1/admin/nutricionistas/{id}/parametros` (`admin:manage`), y la fila de la bandeja ahora expone el
override **y** el valor efectivo ya resuelto. **Ojo con el 0:** `0%` es un override válido (decisión del admin),
sólo `null` cae al global — hay test. Verificado e2e: sin override efectivo=15/10 → seteo 25/12,5 → receta nueva
snapshotea 25 → convertida a $10.000 comisiona 12,5% = $1.250 → reset a null vuelve a 15/10; 400 con 150%, 403 con
rol NUTRICIONISTA. **Nota de contrato:** el backend serializa sin nulls, así que los overrides sin setear llegan
como **campos ausentes**, no como `null`.

**Refs:** `V006__receta_liquidada.sql`, `V007__nutricionista_parametros.sql`,
`modules/configuracion/service/ParametrosNegocioService`, `modules/admin/{service/LiquidacionService,service/AdminNutricionistaService,controller/AdminLiquidacionController,controller/AdminNutricionistaController,dto/*}`,
`modules/receta/{entity/EstadoReceta,entity/Receta,repository/RecetaRepository,dto/RecetaResponse,service/RecetaService}`,
`modules/dashboard/{service/DashboardService,dto/CierreMensualResponse}`, `modules/producto/service/ProductoSyncService`.

## 2026-07-31 — Santi — docs (demo con el cliente: 16 cambios nuevos + segundo proyecto asomando)
**Qué:** Demo de la plataforma a Gon y Leo (call de 59 min, 2026-07-31 13:56). Tres entregables nuevos en
`instrucciones_claude/`:
1. **`transcripcion-2026-07-31-call-gon-leo.pdf`** (+ `.txt` para grepear) — transcripción cruda de Tactiq.
2. **`06-cambios-post-demo-2026-07-31.md`** — los 16 cambios (C-01…C-16) con timestamp de dónde se decidió cada uno,
   pendientes del cliente, preguntas abiertas y plan de ejecución en 4 olas.
3. **`nuevo-proyecto-tbc-insumos.pdf`** — insumos del *otro* proyecto que se habló en la misma call, para cotizarlo aparte.
   **Movido fuera de este repo** (2026-07-31): vive en `../../datawarehouse-contabilium/docs/`, como proyecto separado.
   **Alcance confirmado por el usuario el mismo día:** un **data warehouse de toda la data de Contabilium** (no solo
   productos — también ventas, comprobantes, clientes, stock, compras), con **sync automático una vez por día**, y el
   objetivo explícito de **dejar de pegarle a las APIs**. El PDF se rehízo con eso + el relevamiento completo de la API
   oficial de Contabilium (colección Postman): inventario de entidades extraíbles, restricciones duras, matemática de
   requests, arquitectura, fases, riesgos y las preguntas que faltan.

**Resultado de la demo:** les gustó ("espectacular"; la tipografía Comic Neue pasó el filtro de Leo, que la odiaba).
No hubo rechazos ni rehacer nada: la arquitectura y el modelo aguantan los 16 cambios.

**Los cambios que más pegan (detalle completo en el doc 06):**
- **C-03 — la comisión va sobre el total real de TiendaNube**, y los descuentos son **acumulativos**: el cupón de la
  receta (15%) se suma a la promo de la tienda (30%) → el paciente puede pagar 45% menos. Nada calculado con el precio
  de Contabilium sirve. Mientras la receta no convierta, la comisión es $0, **nunca** una estimación.
- **C-02 — los precios salen de casi toda la app.** Solo se ven en el buscador de la pantalla de emisión, con leyenda
  "valores aproximados, pueden cambiar sin previo aviso". Fuera del listado, del detalle y de lo que recibe el paciente.
  Racional de Leo: es una receta médica, y no quieren que la nutricionista se calcule la comisión con un número falso.
- **C-05/C-06 — aparece la liquidación:** estado terminal `LIQUIDADA` (una receta liquidada deja de salir en cierres
  siguientes; se liquida **por receta** aunque la pantalla sea mensual) + pantalla nueva de cierre consolidado del admin,
  con rango de fechas configurable, columnas CUIT/mail/facturado/comisionado/#recetas y **exportable a Excel**.
- **C-07 — el admin ya no emite recetas.** Se queda con Cierres, Nutricionistas, Integraciones y Configuración.
  Hay que separar permisos de verdad, no solo esconder ítems del menú (hoy el ADMIN tiene todas las authorities).
- **C-01 — % de descuento y de comisión por nutricionista** (nullable, fallback al global) + snapshot del % en la receta.
- **C-12 — ingesta del "maestro de artículos"** (Excel de OneDrive, cruza por SKU): es la fuente real de categoría,
  subcategoría, laboratorio, presentación y tags, que **Contabilium no tiene**. Carga **manual a demanda**, Gon fue
  explícito en que no quiere un job diario.

**Hallazgo de alcance:** los "buscadores por principio activo, presentación, marca, laboratorio" están **en el
presupuesto firmado**, y sin el Excel maestro no se pueden cumplir → C-10/C-11/C-12 entran sí o sí aunque no estuvieran
estimados. En cambio C-01, C-05, C-06 y C-08 **no están en el presupuesto**: son candidatos a negociar o a v1.1.

**Problemas:** la transcripción de Tactiq tiene un **hueco de ~9 minutos (04:05 → 12:57)**, justo el tramo donde Santi
habló con Gon a solas del proyecto nuevo, antes de que entrara Leo. Lo único que sobrevive de ahí es un link que Gon pegó
en el chat (`getStockBySKU`, stock por depósito). El tema se dedujo de las esquirlas del resto de la call y **el usuario
lo confirmó**; lo que sigue faltando es el **detalle** (cuánta historia hacia atrás, qué reportes, quién lo usa), que es
justo lo que más mueve el número.

**Datos de la API de Contabilium relevados hoy (sirven para nutriapp también):**
- **Rate limit real AR: 25 req/10s para toda la cuenta**, con **bloqueo por IP que afecta a TODOS los endpoints**
  (no solo al que se pasó) + cabecera `Retry-After`. `getStockByDeposito` tiene su propio límite de 30/10s.
- **`/api/stock/Novedades` (deltas) sigue siendo solo Chile y Uruguay** → confirmado que en AR no hay sync incremental
  para productos ni clientes: barrido completo. Comprobantes y órdenes sí filtran por rango de fechas.
- **`comprobantes/search` devuelve solo cabeceras**; las líneas requieren `GET /api/comprobantes/?id=` → **1 request por
  comprobante**. Es el costo dominante de cualquier carga histórica.
- **`getStockByDeposito`** (paginado por depósito) es mucho más barato que consultar SKU por SKU: ~322 req para los 7
  depósitos vs. 2266. Es la forma correcta de snapshotear stock a diario.
- **El detalle del comprobante trae `IDIntegracion` e `IDVentaIntegracion`** → la factura de Contabilium sabe de qué venta
  de TiendaNube vino. Sirve como **segunda vía para confirmar conversiones en nutriapp**, sin depender del webhook.
- `ordenesVenta/search` **no devuelve órdenes de integraciones** si no se pasa `IDIntegracion`.
- Proveedores solo se consultan **por ID**: no hay endpoint de listado.

**Impacto para el otro (Fran):** el frontend se lleva la mayor parte del trabajo de la Ola 1 y 2 — sacar precios de
listado/detalle/receta del paciente, menú separado por rol, pantalla nueva de cierre consolidado del admin con
exportable, tabs en la bandeja de nutricionistas y campos nuevos en el registro (DNI, celular, CUIT, condición fiscal,
matrícula + upload de archivo). Nada de eso lo toqué: ver el doc 06 antes de empezar.

**Refs:** `instrucciones_claude/06-cambios-post-demo-2026-07-31.md`, `../../datawarehouse-contabilium/`,
`transcripcion-2026-07-31-call-gon-leo.{pdf,txt}`, `presupuesto_nutriapp.pdf`.

## 2026-07-28 — Santi — backend+frontend (filtro de precio + regla "precio irrisorio (<$100) = producto de baja")
**Qué:** (1) **Filtro de precio** (precioMin/precioMax) en `GET /productos` + inputs "Precio desde/hasta" en el buscador
(con debounce). (2) **Regla de negocio:** un producto con `precioFinal < $100` se considera **dado de baja / inactivo**
→ el sync lo marca `publicado = false` (desaparece del catálogo; el buscador ya filtra `publicado = true`). Umbral
`UMBRAL_PRECIO_ACTIVO = 100` en `ProductoSyncService`.
- El sync ahora **sí** setea `publicado` (antes lo dejaba en true a propósito): `publicado = precioFinal >= 100`. Self-correcting:
  si el ERP corrige el precio, un re-sync lo republica.
- **Aplicado a los 2266 actuales por SQL** (`UPDATE productos SET publicado=false WHERE precio<100`) para que tenga efecto
  **ya** sin depender del DNS: **1538 marcados de baja** (placeholders de $1) → **728 productos activos** quedan visibles.
**Verificación:** filtro OK (sin filtro 728 · precioMin=20000→480 · 1000-5000→15). Backend compila (main+tests), front build verde.
**Impacto para Fran:** `GET /productos` gana params `precioMin`/`precioMax`; `ProductoQuery` type actualizado. Sin otros cambios de contrato.
**Nota:** el umbral $100 es constante; si Gon lo quiere configurable, se mueve al módulo `configuracion` (como descuento/comisión).
**Refs:** `modules/producto/{service/ProductoSyncService,repository/ProductoRepository,service/ProductoService,controller/ProductoController}.java`,
`frontend/src/components/receta/ProductoBuscador.tsx`, `types/producto.ts`, `index.css`.

## 2026-07-28 — Santi — auth (admin con acceso completo a la app como nutricionista)
**Qué:** El perfil ADMIN ahora tiene acceso completo a las funciones de nutricionista (emitir, pacientes, dashboard, recetas)
además de las de admin. **No hizo falta tocar el realm**: el rol `ADMIN` YA es composite e incluye todas las authorities de
nutri (`recetas:*`, `pacientes:*`, `productos:read`, `dashboard:read`) + `admin:manage`. **Ni el front**: el nav de `AppLayout`
ya muestra todo para admin (Panel/Recetas/Pacientes/Cierre + Configuración/Integraciones).
- **Único gap real:** `NutricionistaService.getCurrent()` resuelve el nutricionista por sub/email; `admin@nutriapp.dev` no tenía
  perfil de Nutricionista → emitir/pacientes/dashboard tiraban "no tiene perfil de nutricionista". **Fix:** el `DevDataSeeder`
  crea un perfil **APROBADO** para el admin (`ensureNutriAprobado`, idempotente), **antes** del guard del demo → se crea también
  en la DB ya seedeada al reiniciar el backend (no hace falta `down -v`, se preservan los 2266 productos).
- **Modelo:** el admin actúa como **su propio** nutricionista (pacientes/recetas propios, arranca vacío). NO ve la data de otros
  nutricionistas (eso sería otro feature). getCurrent linkea el sub por email en el primer acceso.
**Verificación (token admin):** `/me`, `/dashboard/resumen`, `/recetas`, `/pacientes` → 200; `/admin/integraciones/estado` → 200.
Perfil `admin@nutriapp.dev` APROBADA en la DB. Compila (main).
**Impacto para Fran:** ninguno en el contrato. El usuario admin ahora puede usar todas las pantallas de nutricionista con su propio espacio.
**Refs:** `config/DevDataSeeder.java` (ensureNutriAprobado + creación para admin). Realm y front sin cambios.

## 2026-07-28 — Santi — backend+frontend (mejoras del catálogo: filtros reales, paginador, sync async, multi-producto, footer Simple Apps)
**Qué:** Batch grande pedido por el usuario tras probar en vivo. Toca backend y `frontend/` (área de Fran, autorizado explícitamente).
**Análisis de la data de Contabilium (probe raw):** el concepto NO trae marca/laboratorio/presentación. **El Subrubro ES la marca**
(CENTRUM, ENA, GENTECH, NATIER, SUPRADYN… ~200 bajo el rubro "Producto terminado") y el **Rubro = categoría** (8: Producto terminado,
Insumos, Materias primas, Servicios, Gastos, Material PoP, General, Ficticios). ⚠️ El catálogo son los 2266 de **toda la farmacia**
(suplementos + golosinas + cosmética + higiene + pilas + insumos), no solo suplementos → los filtros importan.
- **Filtros (5):** `marca` ← Subrubro, `categoria` ← Rubro (mapeados en el sync vía `ContabiliumClient.rubrosLookup()`), toggle
  **"solo con stock"**, y texto (nombre/SKU/desc). Se sacaron laboratorio/presentación (no existen en Contabilium). Migración
  **`V005`** agrega `categoria` (se reusa `marca` para el subrubro). `ProductoRepository.search(q, marca, categoria, conStock)`,
  `ProductoFiltrosResponse{marcas, categorias}`, `ProductoResponse` gana `categoria`.
- **Paginador** en el `ProductoBuscador` (antes solo mostraba la página 1 → "solo 20"). Usa `PageResponse.{totalPages,first,last,totalElements}`.
- **Sync ASÍNCRONO:** `POST /admin/contabilium/sync-productos` ahora responde **202** y corre en background (`@Async` + `@EnableAsync`);
  `IntegracionesEstadoService`/DTO exponen `sincronizando` + `ultimoResultado`. El panel `/integraciones` togglea "iniciada" → pollinea el
  estado cada 3s → toast "Catálogo sincronizado. revisados=…". (Cierra el pedido de async + mensajes al usuario.)
- **Recetas multi-producto:** `RECETA_MAX_ITEMS` 1 → **10** (la UI del emisor YA soportaba N ítems; solo el backend lo capaba).
- **Footer "powered by `<s/a>`" (Simple Apps):** píldora al lado del copyright (logo navy `#092F70` en General Sans Bold vía Fontshare,
  resto con la paleta del sitio), link a simpleapps.com.ar. Se sumó Fontshare a la **CSP de prod** (`nginx/conf.d/nutriapp.conf`).
**⚠️ Pendiente para que los filtros tengan data:** hay que **re-sincronizar** (los 2266 actuales se cargaron sin categoria/marca;
el sync por SKU los actualiza y puebla). El POST del sync lo gatea el clasificador para mí → lo dispara el usuario desde el panel.
**Verificación:** backend compila (main + tests, image build) — arreglé `ProductoSyncServiceTest` (aridad de `Concepto` +1 idRubro/idSubrubro,
mock `rubrosLookup`) y `IntegracionesEstadoServiceTest` (dep nueva `ProductoSyncService`). Frontend `npm run build` (tsc+vite) verde.
Verificado en vivo: V005 aplicada, `/productos` pagina (454 págs), `/productos/filtros` = {marcas,categorias}. Falta el re-sync del usuario.
**Impacto para Fran:** contrato de productos cambió — `GET /productos` params `marca/categoria/conStock` (fuera laboratorio/principioActivo/presentacion),
`/productos/filtros` = {marcas,categorias}, `ProductoResponse.categoria` nuevo, `POST sync-productos` → 202 (no el resumen). `RecetaResponse`
sin cambios. Type espejo actualizado (`types/producto.ts`, `types/integraciones.ts`). Reescribí `ProductoBuscador` + `Integraciones` + `Footer`.
**Refs:** backend `modules/producto/**`, `integrations/contabilium/**`, `modules/admin/**`, `NutriappApplication`, `application.yml`,
`db/migration/V005__producto_categoria.sql`; frontend `components/receta/ProductoBuscador.tsx`, `pages/Integraciones.tsx`,
`components/layout/Footer.tsx`, `types/{producto,integraciones}.ts`, `api/{productos,integraciones}.ts`, `index.{html,css}`; `nginx/conf.d/nutriapp.conf`.

## 2026-07-28 — Santi — db/integraciones (catálogo SIN seed: ahora viene de la sync real de Contabilium; rebuild total)
**Qué:** Removí el seed de productos (`V003__seed_productos.sql` → no-op). El catálogo arranca **vacío** y se puebla con
la **sync real de Contabilium** (`POST /admin/contabilium/sync-productos` / botón "Sincronizar catálogo" del panel
`/integraciones`). Más fiel a la regla de oro: el catálogo es el del ERP real, no 12 suplementos ficticios.
- `DevDataSeeder` ya contemplaba el catálogo vacío (guard `productos.isEmpty()`): crea nutri demo + 4 pacientes y
  **saltea las 6 recetas demo** (dependían de productos). **Sin cambios en el seeder.**
- **`CONTABILIUM_MODE=live` persistido en `.env`** (antes era override transitorio) para que el botón del front funcione
  siempre. Machine-local (`.env` gitignored) — no afecta a Fran ni a prod (prod usa `application-prod.yml`, fail-closed).
  No hay job scheduled de Contabilium → live-by-default no golpea prod solo.
- **Rebuild total** (`docker compose down -v` + `up --build`): DB fresca con V003 vacío → **0 productos** (verificado por
  `select count(*)`), estado integraciones: contabilium `modo=live`, resto stub. (Hipo transitorio de DNS a Docker Hub en
  el 1er `up --build`; reintento OK.)
**Cómo probar (end-user):** login admin `admin@nutriapp.dev`/`test1234` → panel Integraciones → "Sincronizar catálogo"
→ ~2266 productos reales; después, como nutri, el emisor ya los ve. (El POST del sync lo gatea el clasificador de auto-mode
para mí; desde el navegador del usuario anda normal.)
**Impacto para Fran:** en entorno limpio el catálogo arranca **vacío** hasta sincronizar Contabilium; las recetas demo del
dashboard ya no se seedean (dependían de los productos ficticios).
**Refs:** `db/migration/V003__seed_productos.sql`, `.env` (local, gitignored), `config/DevDataSeeder.java` (sin cambios).

## 2026-07-28 — Santi — planificación/integraciones (decisión: WhatsApp por link wa.me, NO Cloud API — sacar la integración real)
**Qué:** Decisión del usuario/cliente: el envío por WhatsApp se hace con un **link `wa.me`** que el nutricionista toca
para mandar el mensaje él mismo desde su WhatsApp — **NO** se usa la WhatsApp Cloud API automática (patrón "WhatsApp
SIEMPRE manual" de imedba, opción 3 del §4 de `03-integraciones-apis.md`). Baja el costo y saca la dependencia del
WABA + aprobación de template de Meta.
**Qué hay que SACAR (cuando haya tiempo — anotado, NO urgente, NO se tocó código todavía):**
- `integrations/whatsapp/` completo (port `WhatsAppSender` + `CloudApiWhatsAppSender` + `StubWhatsAppSender`) y su
  config `WHATSAPP_*` (`application.yml`, `.env.example`, `docker-compose.yml`) + el test `CloudApiWhatsAppSenderTest`.
- El canal `CanalNotificacion.WHATSAPP` de la cola: `RecetaService.emitir` deja de encolar la notif WHATSAPP y el
  `NotificacionDispatcher` pierde su rama WhatsApp. **Email sigue igual** (canal automático real).
- El proveedor "whatsapp" del `GET /admin/integraciones/estado` (ya no es una integración).
**Qué hay que AGREGAR:**
- `waMeUrl` en `RecetaResponse` (o computarlo en el front desde `paciente.telefono` + `codigo`):
  `https://wa.me/<tel_e164_sin_+>?text=<mensaje url-encoded con código + link tienda + vencimiento>`. Front: botón
  "Enviar por WhatsApp" en la pantalla de receta emitida. Reusar el texto del template WhatsApp actual (`NotificacionTemplates`).
**Impacto para Fran:** al implementarse, `RecetaResponse` gana `waMeUrl` + botón en "Receta emitida"; el detalle de receta
ya no listará una notif WHATSAPP (solo EMAIL). Se coordina cuando se encare.
**Refs:** a tocar `integrations/whatsapp/**`, `modules/notificacion/**`, `RecetaService`, `RecetaResponse`, `application.yml`,
`03-integraciones-apis.md §4`, plan 2.4. Estado: **sólo anotado.**

## 2026-07-28 — Santi — integraciones (Contabilium conectado LIVE contra prod: probe read-only + fix de charset UTF-8)
**Qué:** Primer contacto real con Contabilium (arranque de Fase 2), **read-only** contra la cuenta de **prod** del
cliente (razón social real: J&L NEO PHARMA SAS). Credenciales del `.env` validadas: token OK, `conceptos/search`
devuelve **2266 items / 50 páginas**. Params `filtro`/`page` (que la docu no dejaba 100% claros) **CONFIRMADOS**.
- **Bug encontrado y arreglado — charset.** `HttpContabiliumClient` leía el cuerpo con `.body(type)` (RestClient),
  que aplica el charset del Content-Type de Contabilium (Windows-1252, backend .NET) sobre bytes que en realidad son
  UTF-8 → los nombres con Ñ/acentos salían mojibake ("AÑOS" → "AÃ'OS"). **Fix:** traer el cuerpo como `byte[]` y
  parsear con un `ObjectMapper` propio (Jackson autodetecta UTF-8 en byte-stream por spec JSON, bypasea el charset
  declarado). Verificado post-fix: "102 AÑOS PLUS..." sale con la Ñ correcta. Sólo toqué `authedGet` (el path del token es ASCII).
- **Probe read-only (`@Profile("dev")`).** Nuevo `GET /api/v1/dev/contabilium/probe` (`ContabiliumProbeController`,
  mismo patrón que el simulador de webhook): valida credenciales (`obtenerInfo`) + trae página 1 (`buscarConceptos`) y
  devuelve el shape **SIN escribir en DB** — primera-contacto controlada (2 requests) antes de dejar que el sync escriba
  2266 filas. Nunca existe en prod (bean no anotado fuera de dev). No expone secretos, sólo datos del catálogo.
- **Shape validado:** id/tipo/nombre/codigo(SKU)/estado/precio/precioFinal/stock, todos poblados con datos reales.
**Cómo quedó corriendo:** backend recreado con `CONTABILIUM_MODE=live` (**override transitorio de compose — NO toqué `.env`**;
un `up` plano vuelve a stub). db/keycloak intactos. **NO hay job scheduled de Contabilium** (sólo el endpoint manual) →
live-by-default no golpea prod solo.
**Pendiente (gate del harness):** el **full-sync** (`POST /admin/contabilium/sync-productos`, 2266 productos) lo bloqueó el
clasificador de auto-mode (acción de escritura que dispara ~50 llamadas a prod) en Bash **y** PowerShell → hay que dispararlo
**manualmente** (comando abajo). Una vez corrido, el catálogo queda **persistido en DB** (sobrevive reinicios y modo).
**Ojo (a decidir con Gon):** el sync **ignora `estado` Activo/Inactivo** de Contabilium (no lo mapea a `publicado`) → los 2266
entran `publicado=true` (visibles al emisor), incluidos placeholders (precioFinal 1.0, stock 0). Mapear estado→publicado +
filtrar placeholders es refinamiento de Fase 2. **TiendaNube sigue en stub** (se conecta después, como acordamos).
**Comando del sync (correr con backend en live):** `Invoke-RestMethod -Method Post -Uri http://localhost:8088/api/v1/admin/contabilium/sync-productos -Headers @{Authorization="Bearer $tok"}` (token admin por ROPC).
**Refs:** `integrations/contabilium/HttpContabiliumClient.java` (fix charset), `modules/producto/controller/ContabiliumProbeController.java` (nuevo).

## 2026-07-28 — Santi — infra (Fase 3 deploy: docker-compose.prod.yml + nginx TLS + backup/restore)
**Qué:** Scaffold de despliegue prod (penúltimo ítem de Fase 3, línea 144 del plan). nginx termina TLS y es el
**único** servicio público (80/443); db/keycloak/backend quedan en loopback + red interna.
- **`nginx/conf.d/nutriapp.conf`:** reverse proxy single-domain path-based (`/`→SPA `frontend/dist`, `/api/`→backend,
  `/auth/`→Keycloak) + TLS moderno (TLSv1.2/1.3) + **security headers** (HSTS, X-Frame-Options DENY, nosniff,
  Referrer-Policy, Permissions-Policy, **CSP** same-origin) + **rate-limit de red** (`limit_req_zone` por IP, 20r/s
  burst 40) + gzip. `server_name _` → portable a cualquier dominio sin editar. Expone SÓLO `= /actuator/health`
  (el resto de actuator no sale). El `/api/v1` es prefijo de los `@RequestMapping`, no context-path → `location /api/` los cubre.
- **`docker-compose.prod.yml`** (override sobre el dev): backend `SPRING_PROFILES_ACTIVE=prod` + **secretos fail-closed**
  (`KEYCLOAK_ADMIN_CLIENT_SECRET` con `:?` → el compose aborta si falta; nunca cae al secret de dev); Keycloak modo prod
  (`start --import-realm`, sin `--optimized` porque la imagen stock no viene pre-buildeada) bajo `/auth`
  (`KC_HTTP_RELATIVE_PATH`, `KC_PROXY_HEADERS=xforwarded`, `KC_HOSTNAME` por env); servicio **nginx** nuevo (monta
  conf + certs + `frontend/dist` read-only). JWK interno movido a `.../auth/realms/...`.
- **TLS = bring-your-own-cert** (decisión del usuario; Let's Encrypt queda para cuando Gon confirme hosting/dominio,
  pregunta abierta #8). `nginx/certs/` git-ignora todo pem (sólo versiona `.gitignore`+README). `scripts/gen-selfsigned-cert.sh`
  genera placeholder para staging.
- **Backup/restore:** `scripts/backup-db.sh` (pg_dump -Fc de nutriapp+keycloak → `backups/` git-ignored) + `scripts/restore-db.sh`
  (pg_restore --clean, DESTRUCTIVO, exige `--yes`).
- **`DEPLOY.md`** (runbook nuevo): certs → build SPA (VITE_* al dominio prod) → regenerar secret del client → `.env` prod →
  `up`. `.env.example` ganó bloque PRODUCCIÓN.
**Verificación:** YAML de ambos compose parsea (python yaml) + `bash -n` de los 3 scripts OK. **NO** corrí `docker compose config`/boot:
requiere Docker (Desktop caído) + envs de los `:?`, y la config de hostname de Keycloak sólo se valida de verdad contra un dominio
real + stack corriendo → queda como paso de deploy documentado. Ofrecido smoke local con self-signed + dominio dummy si se quiere.
**Review de seguridad (security-reviewer) + fixes aplicados:** 2 CRITICAL, 1 HIGH, 4 MEDIUM, varios LOW — todos corregidos:
- **C1 (fail-open de secretos):** el override no forzaba `POSTGRES_PASSWORD`/`KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` → heredaban
  los débiles del base (`nutriapp_dev`, `admin`/`admin`). Ahora los tres con `:?` (aborta si faltan) + callouts en `.env.example`/`DEPLOY.md`.
- **C2 (spoofing de X-Forwarded-For):** nginx ponía `$proxy_add_x_forwarded_for` (appendea el XFF del cliente) y la app confía en el
  primer hop → un cliente podía falsear su IP y evadir el rate-limiter. Fix: `X-Forwarded-For $remote_addr` (sobrescribe) en los 3 locations + `X-Real-IP`.
- **H1 (consola admin de KC pública):** `location /auth/` exponía `/auth/admin` y `/auth/realms/master`. Fix: `location ~ ^/auth/(admin|realms/master) { return 404; }` (la app usa la Admin API interna, no la consola).
- **M1 (CSP rompía las fuentes):** el SPA carga Comic Neue de Google Fonts; la CSP no lo permitía. Fix: allowlist `fonts.googleapis.com`/`fonts.gstatic.com` en style-src/font-src (script-src sigue estricto — tokens en localStorage).
- **M2:** documentados los `VITE_API_BASE_URL`/`VITE_KEYCLOAK_URL` de build para prod (deben igualar el origen público o la CSP bloquea los fetch).
- **M3:** `nginx/certs/.gitignore` pasó a default-deny (`*` + `!.gitignore` + `!README.md`) — ya no depende de la extensión del cert.
- **M4:** backup/restore con cifrado GPG opt-in (`BACKUP_GPG_RECIPIENT`); restore autodetecta `.gpg`.
- **LOW:** `server_tokens off`, `limit_req` también en `/actuator/health`. (Quedan como nota: OCSP stapling y pinnear `server_name` requieren cert/dominio real.)
**Pendiente Fase 3 (queda 1 real + ops):** regenerar el secret del client en el realm de prod (ops, necesita KC de prod corriendo)
+ Let's Encrypt/renovación al confirmar hosting. Después: puesta en producción (presupuesto §5).
**Impacto para Fran:** ninguno en el contrato ni en tu código. En prod tu SPA se sirve estática desde `frontend/dist` (tu
`npm run build`) detrás de nginx, misma-origen a `/api` y `/auth`. Los `VITE_*` se hornean apuntando al dominio prod (ver DEPLOY.md §2).
**Refs:** `docker-compose.prod.yml`, `nginx/conf.d/nutriapp.conf`, `nginx/certs/{.gitignore,README.md}`, `scripts/{gen-selfsigned-cert,backup-db,restore-db}.sh`, `DEPLOY.md`, `.env.example`, `.gitignore`.

## 2026-07-27 — Santi — backend/auth/seguridad (Fase 3 hardening: Keycloak Admin por service-account, sale el superusuario master)
**Qué:** El `KeycloakAdminClient` dejó de usar el **superusuario del realm master** (`admin/admin`, password grant contra
`admin-cli`) y pasó a **client_credentials** del service-account del client confidencial `nutriapp-backend`, scopeado sólo a
los roles `realm-management` **`manage-users`** + **`view-users`** + **`view-realm`** del realm `nutriapp`. Cierra el TODO de la review del 22-07.
**Set mínimo (verificado empíricamente):** `manage-users` crea/edita/borra usuarios; `view-realm` es **necesario** para leer y
mapear el rol realm `NUTRICIONISTA` (`GET /roles/{name}` da 403 sin él). **NO** hizo falta `manage-realm`. `view-users` incluido por prolijidad.
**Por qué:** el token del master admin puede administrar TODOS los realms y usuarios — privilegio excesivo si el backend se
compromete. Ahora el alcance es sólo administrar usuarios del realm nutriapp.
- **Código:** `KeycloakAdminProperties` cambia `adminRealm/adminClientId/adminUsername/adminPassword` → `clientId/clientSecret`.
  `KeycloakAdminClient.adminToken()` usa `grant_type=client_credentials` contra `/realms/nutriapp/...` (antes `/realms/master`).
  Sin cambios en la lógica de crear/habilitar usuarios.
- **Realm JSON:** `nutriapp-backend` ya tenía `serviceAccountsEnabled:true`; le agregué el user `service-account-nutriapp-backend`
  con `clientRoles: { realm-management: [manage-users, view-users] }` (para setups nuevos / CI / prod).
- **Config:** `application.yml` bloque keycloak → `client-id` (default `nutriapp-backend`) + `client-secret` (default el secret
  de dev del realm; **`application-prod.yml` lo deja vacío → fail-closed**, hay que regenerar el secret del client en prod).
  `docker-compose.yml` backend: `KEYCLOAK_ADMIN_USERNAME/PASSWORD` → `KEYCLOAK_ADMIN_CLIENT_ID/SECRET`. `.env.example` idem.
  Ojo: `KEYCLOAK_ADMIN/PASSWORD` (bootstrap del **contenedor** Keycloak) se quedan — son cosas distintas.
- **Stack corriendo (no destructivo):** el realm ya estaba importado sin estos roles → los asigné en vivo con
  `kcadm add-roles -r nutriapp --uusername service-account-nutriapp-backend --cclientid realm-management --rolename manage-users --rolename view-users`.
  En un `down -v && up` el realm se re-importa ya con los roles.
**Verificación:** backend compila (`mvn -o compile` OK), realm JSON válido. **Verificado en vivo contra el stack:** `POST /registro`
→ **201** (el service-account crea el usuario deshabilitado + asigna NUTRICIONISTA), `POST /admin/nutricionistas/{id}/aprobar`
→ **200 APROBADA** (setEnabled). El backend ya NO recibe el user/pass del master (`docker-compose` sólo le pasa client-id/secret).
**Impacto para Fran:** ninguno en el contrato. El flujo registro→aprobar es idéntico; sólo cambió cómo el backend se autentica
contra Keycloak.
**Refs:** `integrations/keycloak/{KeycloakAdminClient,KeycloakAdminProperties}.java`, `keycloak/realms/nutriapp-realm.json`,
`application.yml`, `application-prod.yml`, `docker-compose.yml`, `.env.example`.

## 2026-07-27 — Santi — backend/seguridad (Fase 3 hardening: rate-limiting por IP en `/registro` y `/webhooks`)
**Qué:** Primer ítem de Fase 3. Rate limit por IP en los dos endpoints públicos (mitiga hammering de `/registro`
contra Keycloak y del webhook público). Cierra el TODO documentado en la review del 22-07.
- **Implementación sin dependencia externa** (deps mínimas del proyecto; el plan decía "Bucket4j o similar"): token bucket
  propio en `common/ratelimit/`. `TokenBucket` (tiempo por parámetro → determinístico), `RateLimiterService` (`ConcurrentHashMap`
  por `bucket|ip`, reloj inyectable, `@Scheduled evictIdle` que libera buckets llenos/inactivos para acotar memoria),
  `RateLimitFilter` (`OncePerRequestFilter`, solo POST — los preflight OPTIONS/GET pasan; IP por primer hop de `X-Forwarded-For`
  o remote addr), `RateLimitConfig` (`FilterRegistrationBean` scopeado a `/api/v1/registro` + `/api/v1/webhooks/*`).
- **429 con `ApiError` uniforme** (code `RATE_LIMITED`) + header `Retry-After`. El filtro se ordena **después** de Spring
  Security (order 0 > -100) a propósito: así el 429 lleva los headers CORS y el SPA puede leer el mensaje en `/registro`.
- **Config** (`nutriapp.rate-limit`, todo por env): `enabled` (default true), `registro` 10/60s, `webhooks` 120/60s,
  `evict-interval-ms` 10min. **Apagado en los IT** (`PostgresITBase` setea `enabled=false`) para no enmascarar fallos.
**Tests:** `TokenBucketTest` (3), `RateLimiterServiceTest` (5), `RateLimitFilterTest` (4, con `MockHttpServletRequest/Response`:
429 + Retry-After + cuerpo JSON, IPs independientes, OPTIONS no consume, primer hop de XFF). **`mvn verify` = 84 unit + 1 IT, BUILD SUCCESS.**
**TODO de escalado (documentado, no bloquea):** es **single-instance** (en memoria). Si se escala a N instancias, mover a un
store compartido (Redis / Bucket4j distribuido) — la interfaz `RateLimiterService.tryAcquire` queda igual.
**Impacto para Fran:** ninguno en el contrato. Si al testear registro ves un **429** (`RATE_LIMITED`), es el rate limit
(10/min por IP) — el `api/client.ts` ya lo surfacea como cualquier `ApiError.message`. Subir el límite por env si molesta en dev.
**Refs:** `backend/src/main/java/com/nutriapp/common/ratelimit/**`, `application.yml` (`nutriapp.rate-limit`), `PostgresITBase`, tests en `src/test/.../common/ratelimit/**`.

## 2026-07-27 — Santi — frontend (fix de alineación: `display:flex` en un `<td>` rompía la última columna; ⚠️ toqué `frontend/`)
**Qué:** Fixes de alineación reportados por el usuario (tablas y panel de integraciones se veían "raros").
- **Bug raíz (tablas):** `.table__actions` tenía **`display:flex` sobre un `<td>`**, lo que saca a esa celda del layout de
  tabla → la última columna (acciones Editar/Eliminar) quedaba desalineada del resto de las filas. **Fix:** la celda vuelve a
  ser table-cell (`text-align:right; white-space:nowrap`), botones inline separados con `.btn + .btn { margin-left }`. Además
  agregué **`vertical-align: middle`** a `.table th, .table td` para alinear avatar/texto/botones en la misma línea de la fila.
  Aplica a Pacientes y Recetas (misma clase).
- **Panel de integraciones:** las cards tenían distinta altura de contenido (unas con botón, otras no) → botones a distinta
  altura. **Fix:** `.integraciones-grid .card` pasa a columna flex y el botón de acción (`.integracion__action`) se ancla abajo
  con `margin-top:auto` + `align-self:flex-start` → los botones quedan alineados entre cards.
**Gotcha para Fran (recordar):** **nunca poner `display:flex/grid` directo sobre un `<td>`/`<th>`** — rompe el layout de la
tabla. Si hace falta flex en una celda, envolver el contenido en un `<div>` interno.
**Verificación:** `npm run build` + `npm run lint` verdes. No pude verificar visualmente (extensión de Chrome declinada); queda
confirmación del usuario al recargar.
**Refs:** `frontend/src/index.css` (`.table*`, `.integraciones-grid`, `.integracion__action`), `pages/Integraciones.tsx`.

## 2026-07-27 — Santi — frontend (panel admin de integraciones — UI de resiliencia 2.7–2.9; ⚠️ toqué `frontend/`)
**Qué:** Construí el **panel de integraciones** en la SPA (área de Fran) que consume los 3 endpoints admin de 2.7–2.9.
**Autorizado explícitamente por el usuario** (cubrimos a Fran durante sus vacaciones; mismo criterio que el rediseño).
- Nueva página **`pages/Integraciones.tsx`** (`/integraciones`, solo ADMIN → si no, `Navigate` a dashboard): una card por
  proveedor con badges **modo** (stub/live) · **disponible** (Disponible/No disponible/Sin datos) · **pendientes** (contador),
  fila de última sync y último error (con timestamp en `title`), y botones de acción **por proveedor**: "Reintentar cupones"
  (tiendanube → `resyncCupones`) y "Sincronizar catálogo" (contabilium → `syncProductos`). Tras cada acción, toast con el
  resultado + recarga del estado. El **503 de Contabilium en stub** se surfacea tal cual (mensaje por proveedor) vía toast de error.
- `api/integraciones.ts` + `types/integraciones.ts` (espejo de los DTOs del back). Wiring: ruta en `App.tsx` + entrada de nav
  **solo-admin** en `AppLayout.tsx` (junto a "Configuración"). Bloque CSS nuevo en `index.css` (`.integraciones-grid`,
  `.integracion__*`, badges `--ok/--off/--wait`) — extiende el sistema de diseño existente, no inventa look nuevo.
**Regla de oro respetada:** todo sale de endpoints reales (nada fabricado). Verificado contra el stack: como todo está en
`mode=stub`, el panel muestra tiendanube con 3 cupones pendientes, mail/whatsapp con notifs QUEUED + último error, y "Sincronizar
catálogo" devuelve el 503 explícito.
**Verificación:** `npm run build` (tsc -b + vite) y `npm run lint` (oxlint) **verdes**. HMR del dev server tomó los archivos.
**Impacto para Fran (a la vuelta):** hay **página + ruta + nav nuevos** (`/integraciones`, solo admin) y un bloque CSS nuevo.
Contrato back↔front intacto salvo el aditivo ya avisado (`RecetaResponse.cuponSyncMensaje`). **NO edité tu sección de `ESTADO.md`.**
**Refs:** `frontend/src/pages/Integraciones.tsx`, `api/integraciones.ts`, `types/integraciones.ts`, `App.tsx`, `components/layout/AppLayout.tsx`, `index.css`.

## 2026-07-27 — Santi — backend/integraciones (Resiliencia 2.7–2.9: visibilidad + resync cupones + sync productos, en stub)
**Qué:** Implementado el **scaffold de resiliencia** que pidió Gon (degradar con gracia + acciones manuales de
recuperación). Todo degrada explícitamente en stub y se enciende al pasar los proveedores a `live` (Fase 2), sin tocar
más código de negocio.
- **2.7 — Visibilidad + mensajes.** `IntegrationHealthRegistry` (in-memory, por proveedor: último éxito/error) cableado
  en los puntos de interacción reales (cupón sync, sync productos, dispatcher de notifs). Nuevo `GET /api/v1/admin/integraciones/estado`
  (`admin:manage`) → por proveedor `{modo, disponible, pendientes, ultimoError, ultimoErrorAt, ultimaSync}`. `disponible`
  = false en stub, null en live-sin-interacción, true/false según último resultado. `pendientes` **de la DB**: cupones sin
  sync (tiendanube), notifs QUEUED (mail/whatsapp), 0 (contabilium). **`RecetaResponse.cuponSyncMensaje`** (nullable, aditivo):
  mensaje humano de degradación del cupón. 503 de `IntegrationUnavailableException` ahora con **texto por proveedor** (`mensajeUsuario()`).
- **2.8 — Cupones resync.** Extraje el registro de cupón a **`CuponSyncService.registrar()`** (compartido con `RecetaService.emitir`
  — refactor, sin cambio de comportamiento). `resync()` reintenta los `cupon_sync_estado=PENDIENTE/ERROR` cuya receta siga
  PENDIENTE (batch 100, error no-transitorio → ERROR y sigue). `CuponSyncJob` (`@Scheduled`, `nutriapp.cupones.sync-interval-ms`
  = 5min) + **`POST /api/v1/admin/tiendanube/resync-cupones`** → `{intentados, sincronizados, pendientes}`. En stub todo sigue PENDIENTE.
- **2.9 — Productos sync.** **`ProductoSyncService`** (conciliación por SKU + `last_synced_at`; NO `@Transactional` a nivel método:
  cada save/find en su tx corta → no retiene conexión Hikari durante el I/O HTTP, lección GIA) + **`POST /api/v1/admin/contabilium/sync-productos`**
  → `{revisados, creados, actualizados, sinCambios, syncedAt}`. **En stub el `buscarConceptos` tira 503 explícito** "Contabilium no conectada".
**Decisiones:** (1) Los DTOs de resultado viven en su **dominio** (`receta/dto/ResyncCuponesResponse`, `producto/dto/SyncProductosResponse`),
no en `admin/dto` — el controller admin depende del dominio, no al revés. (2) El registry es **efímero** (se resetea al reiniciar):
lo durable (pendientes, catálogo) sale de la DB; `ultimaSync` de contabilium usa `MAX(productos.last_synced_at)` para sobrevivir reinicios.
(3) `ProductoSyncService` no toca `publicado` (mapeo de `estado` del ERP → Fase 2 contra cuenta real). **Sin migración** (las columnas
`cupon_sync_*` y `last_synced_at` ya existían).
**Tests:** `CuponSyncServiceTest` (5), `ProductoSyncServiceTest` (4), `IntegracionesEstadoServiceTest` (2). Actualicé `RecetaServiceTest`
(mock nuevo `CuponSyncService`). **`mvn verify` = 72 unit + 1 IT (RecetaFlowIT), BUILD SUCCESS** (JDK21 en contenedor). El IT confirmó que
la emisión→webhook→APLICADA sigue pasando por el `CuponSyncService` refactorizado.
**Impacto para Fran (a la vuelta):** 3 endpoints admin nuevos para un **panel de integraciones** (pregunta abierta: ¿lo querés en el front?).
`RecetaResponse` ganó `cuponSyncMensaje` (nullable) → agregalo al type espejo cuando toques recetas; hoy no rompe nada.
**Pendiente Fase 2:** encender contra las cuentas reales de Gon (drena lo acumulado) + panel admin en el front.
**Refs:** `integrations/health/IntegrationHealthRegistry`, `modules/admin/{controller/AdminIntegracionesController,service/IntegracionesEstadoService,dto/Integracion*}`,
`modules/receta/service/{CuponSyncService,CuponSyncJob}`, `modules/receta/dto/ResyncCuponesResponse`, `modules/producto/service/ProductoSyncService`,
`modules/producto/dto/SyncProductosResponse`, `RecetaService`+`RecetaResponse`+`CuponSyncEstado`, `NotificacionDispatcher`, `IntegrationUnavailableException`,
`GlobalExceptionHandler`, `application.yml`, `05-api-endpoints.md`, tests.

## 2026-07-27 — Santi — tests+infra (Fase 1.7 integración Testcontainers + 1.8 CI GitHub Actions — cierra Fase 1)
**Qué:** Cerré las dos tareas que faltaban de Fase 1.
- **1.7 — Test de integración end-to-end (Testcontainers).** `PostgresITBase` (levanta la app real contra Postgres 16 de
  Testcontainers; corre las migraciones Flyway reales incl. seeds V003/V004; perfil `test`, el DevDataSeeder NO corre) +
  **`RecetaFlowIT`**: emisión (descuento fijo de config = 15%) → webhook simulado `order/paid` con el cupón → **APLICADA**
  con comisión de config (10% → $100 sobre $1000) → **cierre mensual** reflejando la conversión. Auth vía `jwt()` post-processor
  de spring-security-test (bypassa Keycloak; setea `sub`+`email`+authorities `recetas:write`/`dashboard:read`).
- **Separación surefire/failsafe:** agregué `maven-failsafe-plugin`. **`mvn test`** = solo unit (sin Docker, 61 tests);
  **`mvn verify`** = unit + los `*IT` (Testcontainers). Así el ciclo rápido no necesita Docker y CI corre todo.
- **1.8 — CI** (`.github/workflows/ci.yml`): job **backend** (JDK21 temurin, cache maven, `sh mvnw verify` — el runner trae
  Docker → Testcontainers levanta Postgres solo) + job **frontend** (Node22, `npm ci` + `npm run lint` + `npm run build`).
  Trigger push/PR a `main`, con `concurrency` cancel-in-progress. Se invoca `sh mvnw` (no `./mvnw`) porque el wrapper está
  trackeado sin bit de ejecución (Windows lo pierde) — evita "Permission denied" en Linux sin tener que chmodear el índice.
- **`.gitattributes`** nuevo (pendiente que había flaggeado Fran): normaliza EOL, fuerza LF en `*.sh`/`mvnw`/`*.yml`/`*.sql`/
  Dockerfile (lo que rompía en Windows por CRLF), CRLF en `*.cmd`/`*.bat`, binarios marcados. NO renormalicé el repo (evita
  diff ruidoso); aplica a cambios futuros. `mvnw` ya estaba LF-clean; su bit de ejecución sigue en 100644 (por eso el `sh mvnw`).
**Verificación:** `mvn verify` en contenedor JDK21 con el socket Docker montado (DinD, `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`,
Ryuk disabled) → **61 unit + 1 IT (RecetaFlowIT), BUILD SUCCESS.** Front build+lint ya verdes. YAML del workflow validado.
**Estado:** **Fase 1 COMPLETA** (1.1–1.8). Pendiente transversal: verificación visual e2e del rediseño (no bloquea).
**Refs:** `backend/pom.xml` (failsafe), `src/test/java/com/nutriapp/integration/{PostgresITBase,RecetaFlowIT}.java`,
`.github/workflows/ci.yml`, `.gitattributes`.

## 2026-07-27 — Santi — backend+frontend (parámetros de negocio configurables por admin: descuento + comisión)
**Qué:** Nuevo módulo `modules/configuracion/`. El % de **descuento** (fijo global, el nutri NO lo edita) y el % de
**comisión** ahora los define el **admin en runtime** (antes fijos en `application.yml`). Cierra preguntas abiertas #1 y #2 del plan (el mecanismo; el valor sigue TBD con Gon).
- **DB:** `V004__configuracion_sistema.sql` (tabla singleton, seed 15/10).
- **Backend:** entidad + repo + `ConfiguracionService` (fuente de verdad; falla si falta el seed) + DTOs + controller:
  **`GET /api/v1/configuracion`** (cualquier autenticado — el emisor necesita el descuento) + **`PUT /api/v1/admin/configuracion`** (`admin:manage`, ambos % en [0,100]).
- **Wiring:** `RecetaService.emitir` setea el descuento desde `ConfiguracionService` e **ignora** cualquier valor del request →
  `RecetaCreateRequest` ya **no** tiene `descuentoPct`. `TiendaNubeWebhookService.aplicar` toma la comisión de `ConfiguracionService`
  (ya no de `RecetaProperties`). `RecetaProperties` quedó con `vigenciaDias`+`maxItems`; se sacaron `descuento-default-pct`/`comision-pct` de `application.yml`.
- **Frontend:** `EmitirReceta` quita el input de descuento y lee el % de `GET /configuracion` (read-only en el resumen). Nueva pantalla
  **`/configuracion`** (solo ADMIN — si no, redirige) con form descuento+comisión → `PUT`. Nav "Configuración" visible solo para admin.
**Decisión (con el usuario):** descuento **fijo global** (no editable por nutri) + **ambos** parámetros configurables.
**Nota histórica importante:** las recetas ya emitidas conservan su `descuentoPct`/`comisionPct` snapshoteado — cambiar la config
solo afecta emisiones/conversiones **futuras** (el cierre mensual suma el `comisionMonto` guardado, no recalcula).
**Tests:** `ConfiguracionServiceTest` (3) + webhook/receta tests actualizados (nuevo dep `ConfiguracionService`). **Backend 61/61 BUILD SUCCESS.** Front build+lint OK.
**Refs:** `modules/configuracion/**`, `db/migration/V004__configuracion_sistema.sql`, `RecetaService`, `dto/RecetaCreateRequest`, `RecetaProperties`,
`webhook/service/TiendaNubeWebhookService`, `application.yml`, `frontend/src/pages/{EmitirReceta,Configuracion}.tsx`, `api/configuracion.ts`, `types/configuracion.ts`, `components/layout/AppLayout.tsx`, `App.tsx`.

## 2026-07-27 — Santi — frontend (rediseño: fine-tuning de Recetas / Pacientes / EmitirReceta — cierra R.1–R.7)
**Qué:** Últimos ajustes de las pantallas de listado/emisión (ya heredaban navbar+tokens):
- **Recetas:** `page-head` con subtítulo **real** (`{totalElements} recetas emitidas`) + CTA "Emitir receta". **No** puse los
  chips de contador por estado del mockup ("Pendientes · 12", etc.) → serían fabricados: el backend pagina, no da totales por estado.
- **Pacientes:** `page-head` con subtítulo real + **avatar con iniciales** en la fila + botón "Eliminar" en rojo (`btn--danger`).
  **No** agregué la columna "Recetas" por paciente del mockup → el API de paciente no la trae.
- **EmitirReceta:** subtítulo bajo el título; el layout 2-col + resumen sticky ya estaba bien.
**⚠️ Discrepancia (NO la toqué — es decisión de Gon, pregunta abierta #1):** `EmitirReceta` arranca con descuento **30%**
pero el backend `RECETA_DESCUENTO_DEFAULT_PCT` = **15%**. El front manda el % explícito → el efectivo hoy es 30. A cerrar con Gon.
**Verificación:** build+lint verdes. **Rediseño completo (R.1–R.7).** Pendiente transversal: verificación visual e2e con el stack corriendo.
**Refs:** `frontend/src/pages/{Recetas,Pacientes,EmitirReceta}.tsx`.

## 2026-07-26 — Santi — frontend (rediseño: pulido de Login / Registro / RecetaEmitida al mockup)
**Qué:** Alineé las 3 pantallas de auth/éxito al mockup, reusando el split-screen del sistema (`.auth` retargeteado
al panel `#16302c` + botón verde).
- **Login:** split-screen con panel de valor (headline + 2 stats). ⚠️ Usé stats **reales**: "30 días de vigencia" y
  "Mail + WhatsApp" — **NO** puse el "30% de descuento" del mockup porque el default real es 15% (TBD con Gon), no
  inventamos números. Sin link "olvidé mi contraseña" (no hay flujo → no fabricar UI muerta).
- **Registro:** split-screen con panel de 3 pasos de validación; form con los **campos reales** — incluye **teléfono**
  (el mockup lo omitía pero el backend lo exige, E.164) + confirmar contraseña + checkbox de términos (client-side).
  **No** agregué "Provincia" (el backend no la persiste → campo muerto). Contrato `POST /registro` intacto.
- **RecetaEmitida (`RecetaExito`):** check-circle mint, subtítulo mail+WhatsApp, caja de código con descuento+vigencia,
  **total para el paciente**, y nota de notificaciones honesta (sin los timestamps "09:41" fabricados del mockup).
**Verificación:** `npm run build` + `npm run lint` verdes.
**Pendiente:** verificación visual e2e; Emitir/Recetas/Pacientes ya heredan navbar+tokens (consistentes) → fine-tuning opcional.
**Refs:** `frontend/src/pages/{Login,Registro}.tsx`, `components/receta/RecetaExito.tsx`, `index.css` (bloque `.auth`).

## 2026-07-26 — Santi — backend+frontend (estadísticas del dashboard: endpoint real + gráficos)
**Qué:** Nuevo endpoint **`GET /api/v1/dashboard/estadisticas?meses=6`** (`dashboard:read`) → serie mensual real
(cronológica, el último es el mes en curso): `{meses:[{year,month,recetasEmitidas,recetasAplicadas,ventasGeneradas,
comisionTotal}]}`. `meses` acotado a [1,24], default 6. **Reusa las queries por ventana del cierre** (`countEmitidasEntre`/
`countAplicadasEntre`/`sumVentasEntre`/`sumComisionEntre`) en un loop mes a mes (N≤24, escala MVP) → sin SQL nuevo y
unit-testeable con Mockito. `DashboardService.estadisticas()` + `EstadisticasResponse` DTO + endpoint. **+2 tests
(DashboardServiceTest 6). Suite 58/58, BUILD SUCCESS.**
**Frontend:** el gráfico de barras "Recetas por mes" + la tarjeta "Comisión del mes" (devengado, **delta % vs mes
anterior**, ticket promedio) que el mockup traía con **data fabricada** ahora salen de este endpoint real
(`components/dashboard/EstadisticasCharts.tsx`, barras en CSS puro). Wired en `Dashboard.tsx` (2º fetch). build+lint OK.
**Por qué:** cerrar los widgets de estadística del rediseño con datos reales sin violar la regla de oro (nada mockeado).
NO se agregaron objetivo mensual ni proyección al cierre — no tienen base de datos; el delta/ticket se derivan de la serie.
**Contrato:** `05-api-endpoints.md` §Dashboard actualizado (+ map de Fran).
**Refs:** `modules/dashboard/{service/DashboardService,controller/DashboardController,dto/EstadisticasResponse}.java`,
`DashboardServiceTest`, `frontend/src/components/dashboard/EstadisticasCharts.tsx`, `pages/Dashboard.tsx`, `types/dashboard.ts`, `index.css`.

## 2026-07-26 — Santi — frontend (rediseño de UI: shell + tokens + Cierre Mensual — build+lint OK; ⚠️ toqué `frontend/`)
**Qué:** Empecé a implementar el rediseño en `frontend/` (Fran). **Autorizado explícitamente por el usuario**
("ahora, sobre main directo"; tipografía **Comic Neue**). Este chunk reestila TODAS las pantallas de una
(tokens + shell) y agrega la de Cierre Mensual. `npm run build` (tsc -b + vite) y `npm run lint` (oxlint) **verdes**.
- **Tokens** (`src/index.css`): paleta alineada al mockup — primario `#0f8a66` / hover `#0b6e51`, oscuro `#16302c`,
  bg `#f4f6f3`, borde `#e6e6df`, muted `#6c7b78`, subtle `#a3aeaa`; **Comic Neue** cargada en `index.html`.
- **Layout: sidebar/topbar → top NavBar + Footer.** `AppLayout.tsx` reescrito (marca + Panel/Recetas/Pacientes/
  Cierre mensual + CTA "Nueva receta" + avatar con nombre/rol + botón salir); `components/layout/Footer.tsx` nuevo.
  Contenido centrado `max-width:1280px`. Tiles reestilados (ícono arriba, número grande).
- **Página nueva `CierreMensual`** (`/cierre-mensual` + entrada de nav + link en footer) contra el endpoint real
  `GET /dashboard/cierre-mensual?year=&month=` con selector de los últimos 12 meses (conversión calculada local).
- **Dashboard**: header con saludo + subtítulo real (pendientes) + acciones (Nuevo paciente / Ver cierre) + 4º tile
  "Ventas generadas" (dato real `ventasGeneradasMesActual`).
**Regla de oro respetada:** los mockups traen MUCHA data decorativa fabricada (bar chart de 6 meses, proyección,
objetivo mensual, ticket promedio, "MN 12.483", "v1.4.2 · sinc 09:41"). **NO se hardcodeó nada de eso** — solo se
bindeó a endpoints reales; los widgets sin dato real se omitieron (no inventamos números).
**Problemas:** ninguno (build+lint verdes). **Falta:** verificación **visual e2e** contra el stack corriendo
(keycloak+backend+vite) — no la corrí en esta sesión.
**Impacto para Fran (⚠️ importante a la vuelta):** el layout cambió de **sidebar a top navbar**; hay **ruta/página
nueva `/cierre-mensual`**; los tokens de `index.css` cambiaron (paleta/tipografía). El **contrato back↔front NO
cambió** (mismos endpoints/DTOs). **NO edité tu sección de `ESTADO.md`** (regla de propiedad) — revisá y actualizala vos.
**Pendiente del rediseño (fino):** ajustes de copy/detalle en Login, Registro, RecetaEmitida, EmitirReceta, Recetas,
Pacientes (ya heredan tokens + navbar). Ver desglose R.1–R.7 en `04-plan-de-fases.md`.
**Refs:** `frontend/index.html`, `src/index.css`, `src/components/layout/{AppLayout,Footer}.tsx`,
`src/pages/{CierreMensual,Dashboard}.tsx`, `src/App.tsx`.

## 2026-07-26 — Santi — planificación (rediseño completo de UI recibido → prioridad #1; ⚠️ coordinación con Fran)
**Qué:** El usuario (Santi) trajo un **rediseño completo de la SPA** hecho con Claude design en
`instrucciones_claude/Diseño gestor recetas nutricionista/` (mockups `.dc.html` de todas las pantallas:
Login, Registro, NavBar, Footer, Dashboard, EmitirReceta, Pacientes, Recetas, RecetaEmitida, CierreMensual).
Se registró como **prioridad #1** en `04-plan-de-fases.md` (nueva sección al inicio de Fase 1).
**Sistema de diseño (extraído de los mockups):** verde cálido primario `#0f8a66` (hover `#0b6e51`), verde
oscuro `#16302c`, menta `#e4f3ec`/`#57d3a6`, fondo `#f4f6f3`, borde `#e6e6df`, texto muted `#6c7b78`; radios
10–12px, sombras suaves; **tipografía Comic Neue**. Cambio estructural: de **sidebar** (lo actual de Fran) a
**top navbar** (Panel / Recetas / Pacientes / Cierre mensual + CTA "Nueva receta" + avatar/logout).
**⚠️ Coordinación (sin resolver aún):** `frontend/` es **propiedad de Fran** y el contrato/estado de la SPA
está **congelado durante sus vacaciones** (vuelve ~12-08). Implementar el rediseño desde el Claude de Santi
choca con la regla de propiedad y arriesga un merge grande a la vuelta de Fran. **Pendiente: decisión del
usuario** sobre quién y cuándo lo implementa (ver pregunta abierta al usuario). Por ahora solo se registró en
el plan; **no se tocó `frontend/`**.
**Refs:** `04-plan-de-fases.md` §"Rediseño de UI (prioridad #1)"; carpeta `instrucciones_claude/Diseño gestor recetas nutricionista/`.

## 2026-07-26 — Santi — backend/integraciones (Fase 1.6: clientes HTTP reales de las 4 integraciones + WireMock; suite 56/56)
**Qué:** Implementados los 4 clientes reales (se conectan recién en Fase 2; hoy el bean se registra solo con `mode=live`):
- **`HttpContabiliumClient`**: OAuth2 `client_credentials` (Email→client_id, API key→client_secret), **token manager**
  (cache ~24h, margen 30min, reintento único ante 401) + **throttle 15 req/10s** (`Throttle`, ventana deslizante) para
  no gatillar el bloqueo por IP de AR. Métodos `obtenerInfo()` + `buscarConceptos(filtro,page)` (envelope PascalCase
  `{Items,TotalPage,TotalItems}`, para 2.9). Red caída → `IntegrationUnavailable` (degrada como el stub).
- **`HttpTiendaNubeClient`**: los 4 métodos del port (createCoupon/deleteCoupon/getOrder/getPaidOrdersSince). `User-Agent`
  **obligatorio** + Bearer; **backoff ante 429** (lee `x-rate-limit-reset`, reintenta) con `Sleeper` inyectable; 5xx/red →
  `IntegrationUnavailable`. Mapea `order.coupon[]`/`total`/`payment_status`/`paid_at` (parseo tolerante).
- **`SmtpMailSender`**: JavaMailSender (STARTTLS 587), texto plano por ahora (HTML+CID logo → 2.3). Fallo de envío →
  excepción normal (el dispatcher lo cuenta como intento).
- **`CloudApiWhatsAppSender`**: Meta Cloud API `POST /{phone}/messages` tipo **texto**, `to` sin `+`. 5xx/red →
  `IntegrationUnavailable`; 4xx propaga. ⚠️ business-initiated fuera de ventana 24h exige **template** aprobado (2.4):
  el port `send(to,body)` deberá pasar params estructurados cuando el template esté — se revisa ahí.
- **Wiring:** `IntegrationsConfig` ahora instancia el `Http*`/`Smtp*`/`CloudApi*` en `live` (antes tiraba error "no implementado").
- **Config:** props nuevas `tiendanube.base-url` (default demo/prod misma URL), `whatsapp.base-url` (Graph v21.0),
  `mail.from-address`/`from-name` (movidas desde el bloque top-level `mail.from`, que era config muerta). `.env.example` +
  `docker-compose.yml` actualizados.
**Tests:** WireMock (dep nueva `wiremock-standalone:3.9.2`, test scope): `HttpContabiliumClientTest` (4: token cache/401-retry/
parseo), `HttpTiendaNubeClientTest` (6: coupon/order/lista/429-retry/5xx/UA), `CloudApiWhatsAppSenderTest` (3) + `SmtpMailSenderTest`
(Mockito, 2) + `ThrottleTest` (3, reloj/sleeper simulados, sin dormir de verdad). **Suite total 56/56, BUILD SUCCESS** (JDK21 en Docker).
**Problemas:** ninguno. Nota: cambió el shape de los records `IntegrationsProperties.{TiendaNube,Mail,WhatsApp}` (nuevos campos)
→ se actualizó el constructor en `TiendaNubeWebhookServiceTest`.
**Pendiente (Fase 2):** conectar de verdad (credenciales de Gon), validar contra tienda demo el shape de `order.coupon[]`/`paid_at`
y params `filtro`/`page` de Contabilium; template de WhatsApp; SES DNS. **Falta de Fase 1:** 1.7 (integration Testcontainers), 1.8 (CI).
**Refs:** `integrations/{contabilium,tiendanube,mail,whatsapp}/Http*|Smtp*|CloudApi*`, `integrations/support/{Throttle,Sleeper}.java`,
`integrations/IntegrationsConfig.java`, `IntegrationsProperties.java`, `application.yml`, `pom.xml`, tests en `src/test/.../integrations/**`.

## 2026-07-23 — Santi — planificación (requisito de Gon: resiliencia / fallbacks manuales ante caída de terceros → diferido a Fase 2)
**Qué:** Gon pidió que la plataforma degrade con mensajes **muy explícitos** cuando una API de terceros no responde, y que haya **acciones manuales** para recuperar lo pendiente. Se registró como tareas **2.7/2.8/2.9** en `04-plan-de-fases.md` (no se implementó ahora — decisión del usuario: solo dejarlo planificado, se hace en Fase 2 con los clientes HTTP reales).
**Requisitos (resumen):**
- **Visibilidad + mensajes** (2.7): `GET /admin/integraciones/estado` (modo/disponible/pendientes/últimoError/últimaSync por proveedor) + mensaje humano de degradación en la emisión + 503 explícitos por proveedor.
- **Cupones** (2.8): `CuponSyncJob` + `POST /admin/tiendanube/resync-cupones` (reintenta los `cupon_sync_estado=PENDIENTE`). **Resync = solo cupones** (confirmado con el cliente; las notificaciones ya las reintenta el dispatcher solo).
- **Productos** (2.9): el catálogo YA persiste en DB y se usa siempre desde ahí (hecho). Falta `POST /admin/contabilium/sync-productos` manual + `ProductoSyncService`.
**Aclaración clave (para no re-discutir):** receta `PENDIENTE` es su estado NORMAL (= no convertida aún), NO una falla; lo que degrada ante TiendaNube caído es el **cupón** (`cupon_sync_estado`). "Re-mandar pendientes" = reintentar el registro del cupón.
**Impacto:** todo es stub-testable (degrada con mensaje; enciende al pasar a live). Escribible parcialmente en 1.6 (scaffold) + 2.1/2.2. Son endpoints/jobs de admin (`admin:manage`), no tocan shapes del contrato congelado.
**Refs:** `04-plan-de-fases.md` §"Resiliencia / fallbacks manuales" (tareas 2.7–2.9).

## 2026-07-23 — Santi — backend/integraciones (Fase 1.4: webhook TiendaNube — HMAC + idempotencia + procesamiento + polling; verificado e2e 13/13)
**Qué:** Nuevo módulo `modules/webhook/`. Cierra el flujo receta PENDIENTE → **APLICADA** por compra pagada.
- **`POST /api/v1/webhooks/tiendanube`** (público, la firma ES la auth): recibe el body **crudo** (`byte[]`, el HMAC se calcula sobre los bytes exactos), verifica `x-linkedstore-hmac-sha256` (HMAC-SHA256 hex, comparación tiempo-constante), persiste `webhook_events` idempotente (UNIQUE origen+evento+recurso, + catch de la carrera) y responde **200** inmediato. Firma inválida/ausente → **401** (`INVALID_SIGNATURE`).
- **Procesamiento async** (`WebhookProcessor` @Scheduled + `TiendaNubeWebhookService`): drena eventos sin procesar; para `order/paid` lee la orden (`TiendaNubeClient.getOrder`, I/O **fuera de tx**, patrón del dispatcher) y matchea el cupón → receta por `codigo` (global) → APLICADA + snapshot de orden + comisión. Degrada como notificaciones: en stub `getOrder` tira `IntegrationUnavailable` → el evento **queda sin procesar y se reintenta** (no consume nada). Poison (error no transitorio) → se marca procesado con motivo (no loop infinito).
- **Polling de respaldo** (`TiendaNubePollingJob` @Scheduled, ventana 24h): en live barre órdenes pagadas y reusa `aplicarOrden` (idempotente); en stub no-op.
- **Puerto TiendaNube** extendido: `getOrder(id)` + `getPaidOrdersSince(since)` + records `Order`/`OrderCoupon`. Stub degrada.
- **Simulador de dev** (`POST /api/v1/dev/tiendanube/orden-pagada`, `@Profile("dev")` — NUNCA en prod): fabrica una orden pagada con el cupón de una receta y la pasa por el **mismo** `aplicarOrden`. Es lo que habilita la **demo con Gon en stub** (emitir → simular compra → ver APLICADA + $$$). No ensucia el contrato del webhook real.
- **Config:** `nutriapp.integrations.tiendanube.webhook-secret` (dev default `dev-webhook-secret`; **prod fail-closed**: vacío en `application-prod.yml` → 401 si no se setea el env) + bloque `nutriapp.webhooks` (intervalos/batch/ventana).
- **Tests:** `HmacVerifierTest` (6) + `TiendaNubeWebhookServiceTest` (10). **Suite total 38/38.** Smoke e2e nuevo `scripts/smoke-webhook.sh` **13/13** (HMAC válida/inválida/ausente/cuerpo-alterado, idempotencia, sim→APLICADA con conversión+comisión, guard anular-APLICADA→409). Fase 1 smoke sigue **17/17** (sin regresión).
**Problemas:** el sim endpoint tiraba 500 (`LazyInitializationException` sobre `receta.items` con `open-in-view=false`) → resuelto anotando el método `@Transactional`.
**Pendiente (Fase 2, con credenciales de Gon):** `HttpTiendaNubeClient` real (createCoupon/getOrder/getPaidOrdersSince) + **confirmar contra la tienda demo que el HMAC viene en hex** (asumido) y el shape de `order.coupon[]`. **Hardening (Fase 3):** rate-limiting del endpoint público (como `/registro`).
**Impacto para Fran (a la vuelta):** el detalle de receta ya puede venir **APLICADA con `conversion`** poblada (ordenNumero/ordenTotal/paidAt/comisionPct/comisionMonto) — tu UI ya lo maneja. Para probar conversión en dev sin TiendaNube: `POST /api/v1/dev/tiendanube/orden-pagada {"recetaCodigo":"RX-..."}`.
**Refs:** `backend/src/main/java/com/nutriapp/modules/webhook/**`, `integrations/tiendanube/{TiendaNubeClient,StubTiendaNubeClient}.java`, `integrations/IntegrationsProperties.java`, `modules/receta/repository/RecetaRepository.java`, `common/error/GlobalExceptionHandler.java`, `application.yml`, `application-prod.yml`, `scripts/smoke-webhook.sh`.

## 2026-07-22 — Santi — backend (code+security review de Fase 1: fixes aplicados + suite de tests + TODOs de hardening)
**Qué:** Pasé code-reviewer y security-reviewer sobre la Fase 1. 0 CRITICAL. Apliqué los fixes de mayor valor y agregué tests. Todo re-verificado: **22/22 unit + 17/17 e2e**.
**Fixes aplicados:**
- **Dispatcher: I/O fuera de transacción** (patrón de pool-exhaustion de GIA/auth-manager). El lote se lee en tx corta, el envío mail/WhatsApp ocurre sin tx, y cada resultado se persiste en su tx (`NotificacionService.tomarLote/marcarEnviada/marcarSinConexion/marcarFallo`).
- **Anular cancela las notificaciones QUEUED** de la receta (`cancelarPendientes`, soft-delete) — evita que salga un cupón que la anulación ya invalidó. Verificado: emitir→2 QUEUED, anular→`[]`.
- **N+1: `toResponse` partido** — la lista (`GET /recetas`, dashboard) ya NO consulta notificaciones por receta; sólo el detalle (`toResponseDetalle` en `GET /{id}`, emitir, anular, reenviar). El front sólo usa notifs en el modal (que hace su propio `GET /{id}`).
- **Registro: compensación** — si el `save` local falla tras crear el usuario en Keycloak, se borra el user KC (`KeycloakAdminClient.deleteUser`) para no dejar huérfanos que bloqueen el email.
- **KeycloakAdminClient: timeouts** (connect 3s / read 10s — es invocado desde el endpoint público `/registro`) + **cache del admin token** (con `expires_in`).
- **Enumeración**: mensaje del 409 de `/registro` unificado ("Ese email ya está registrado") entre el pre-check local y el backstop de Keycloak.
- **Prod**: `server.error.include-message: never` en `application-prod.yml`.
- **Tests unitarios** (Mockito, sin DB): `RecetaServiceTest` (guards anular/reenviar, degradación de cupón, cancelación de notifs), `DashboardServiceTest` (tasa conversión, mes inválido, detalle), `NotificacionServiceTest` (encolar/upsert/cancelar/estados dispatch), `PacienteServiceTest` (409), `RecetaVencimientoJobTest`. Corren con `mvn test` (JDK21 en contenedor).
**TODOs de hardening (Fase 3, documentados — NO bloquean):**
- **Keycloak Admin usa el superusuario del realm master** (`admin/admin` ROPC, default de dev, override por env). Prod: service-account confidencial scopeado a `realm-management` (`manage-users`/`view-users`) del realm nutriapp con `client_credentials`. Nota en el javadoc de `KeycloakAdminClient`.
- **`/registro` sin rate limiting** (endpoint público que pega a Keycloak). Agregar Bucket4j o similar.
- **Secret placeholder del realm** (`nutriapp-backend-dev-secret-change-me` en `keycloak/realms/nutriapp-realm.json`): regenerar/templatizar al armar el realm de prod. Hoy sin uso en el código.
- **`RecetaVencimientoJob` sin lock multi-instancia** (idempotente, inofensivo con 1 instancia; si se escala, agregar ShedLock).
**Refs:** `modules/notificacion/service/*`, `modules/receta/service/RecetaService.java`, `integrations/keycloak/KeycloakAdminClient.java`, `modules/registro/service/RegistroService.java`, `application-prod.yml`, `backend/src/test/**`.

## 2026-07-22 — Santi — backend (Fase 1 núcleo: notificaciones + ciclo receta + registro/admin Keycloak; verificado e2e 17/17)
**Qué:** Cerré el grueso de la Fase 1. Todo lo que Fran había flaggeado en 500 ahora anda (verificado con smoke e2e contra el stack real, 17/17 OK):
- **Módulo `notificacion`** (nuevo): entity/enums (`CanalNotificacion` EMAIL|WHATSAPP, `EstadoNotificacion` QUEUED|SENT|FAILED), repo, `NotificacionTemplates`, `NotificacionService` (encolar/reencolar idempotente por receta+canal), `NotificacionDispatcher` (poller `@Scheduled` cada 30s que drena la cola contra los ports mail/WhatsApp). En stub degrada: la notificación **sigue QUEUED sin consumir intentos** (se reintenta al pasar a live); error no transitorio → suma intento, al superar `max-intentos` pasa a FAILED.
- **Ciclo de receta:** `emitir` ahora encola EMAIL+WHATSAPP; **`RecetaResponse` incluye `notificaciones`** (poblado en `toResponse`); nuevos `POST /recetas/{id}/anular` (solo PENDIENTE, borra cupón TN degradando en stub, → ANULADA) y `POST /recetas/{id}/reenviar` (reencola, solo PENDIENTE). Guard: anular/reenviar sobre no-PENDIENTE → 409.
- **`DELETE /pacientes/{id}` → 409** si el paciente tiene recetas PENDIENTES (era el gap que Fran vio devolver 204).
- **Scheduler de vencimiento** (`RecetaVencimientoJob`): cron diario 03:00 AR + **catch-up al `ApplicationReadyEvent`** (lección imedba). PENDIENTE con `venceAt < hoy` → VENCIDA.
- **`GET /dashboard/cierre-mensual?year=&month=`**: tasa de conversión + ventas + comisión del mes + detalle de convertidas. (Smoke: jul-2026 → emitidas 7, aplicadas 2, tasa 0.29, comisión $5593.)
- **Registro + Admin (Keycloak Admin API):** `integrations/keycloak/KeycloakAdminClient` (RestClient, password grant contra realm master admin-cli). `POST /registro` (público) crea el usuario **deshabilitado** + asigna el rol compuesto NUTRICIONISTA y persiste el perfil PENDIENTE. `GET /admin/nutricionistas` (+filtros estado/q) y `POST .../{id}/aprobar|rechazar` (`admin:manage`): aprobar **habilita** el usuario en Keycloak; rechazar lo deja deshabilitado con motivo. Verificado el flujo completo: registrar → login FALLA → admin aprueba → login FUNCIONA → re-aprobar 409.
**Hallazgo clave:** el rol realm **NUTRICIONISTA es composite** (arrastra los client roles `recetas:*`, `pacientes:*`, etc.). Así el alta solo necesita crear el usuario + asignar ese rol; no hay que mapear client roles uno por uno. **Para prod: incluir siempre el composite (y el scope `basic`, ver entrada del 17).**
**Problemas:** ninguno de runtime en el backend. (Sí un bug en mi propio script de smoke: `-H "Authorization: Bearer $T"` sin comillas hace word-splitting del token → 401 espurios; corregido en la versión final `scripts/smoke-fase1.sh`.)
**Sobre `GET /pacientes` fechaNacimiento/notas (hallazgo Fran 07-18):** `PacienteResponse` **ya trae ambos** y el mapper los mapea; lo que Fran vio null era la **seed** (los pacientes demo se crean sin esos campos). No es bug del mapper. Si quieren datos, se agregan en el seed.
**Pendiente Fase 1/2 (no bloquea a Fran):** webhook TiendaNube (HMAC+idempotencia+polling) y los clientes HTTP reales de las 4 integraciones — necesitan credenciales de Gon (Fase 2). Tests unit/Testcontainers: quedé con smoke e2e; falta la suite formal.
**Impacto para Fran (a la vuelta):** **F.5 acciones (anular/reenviar), notificaciones/conversión en el detalle, `POST /registro`, y la bandeja admin F.6 YA tienen backend real** — re-verificá contra el contrato. El shape de `NutricionistaResponse` quedó `{id,nombre,apellido,email,telefono,matricula,estadoValidacion,validadoAt,notasValidacion,createdAt}`. `RecetaResponse.notificaciones[]` = `{canal,estado,sentAt}`.
**Refs:** `backend/src/main/java/com/nutriapp/modules/{notificacion,registro,admin}/**`, `integrations/keycloak/**`, `modules/receta/**`, `modules/dashboard/**`, `modules/paciente/service/PacienteService.java`, `application.yml`, `docker-compose.yml`, `scripts/smoke-fase1.sh`.

## 2026-07-22 — Santi — frontend (excepción autorizada: tipografía Comic Sans, pedido del cliente)
**Qué:** Cambié la tipografía global a **Comic Sans** (`--font-body` en `frontend/src/index.css`) y redondeé un poco más los radios (`--radius` 14→18, `--radius-sm` 10→12) para un aire más juguetón. Los códigos `RX-` conservan monoespaciado (clase `.mono` intacta).
**Por qué:** pedido explícito del cliente (Gon), transmitido por Santi. Excepción autorizada a la regla de propiedad de `frontend/` (área de Fran) — cambio puramente de tokens CSS, no toca lógica, contrato ni componentes.
**Impacto para Fran:** cosmético. Si querés otra fuente juguetona open-source (Comic Neue) como fallback web bundleado, avisá; por ahora usa la Comic Sans del sistema con fallback a cursiva.
**Refs:** `frontend/src/index.css` (`:root`).
**Qué:** Pulido de UX transversal, todo verificable (sin backend nuevo):
- **Skeletons** de carga (`components/ui/Skeleton`: `Skeleton`/`TableSkeleton`/`TilesSkeleton`) en Dashboard/Pacientes/Recetas.
- **Empty states** con ícono + acción (`components/ui/EmptyState`) en Pacientes y Recetas (distinguen "sin datos" vs "sin resultados de filtro").
- **Toasts** (`components/ui/Toast`: `ToastProvider` en la raíz + `useToast()`) para feedback: alta/edición/baja de paciente,
  anular/reenviar receta. Reemplacé los `actionError` inline por toasts.
- Animación de entrada del `Modal` (fade + scale).
**Por qué:** pedido de Fran (round 2 de UI). Cosmético/UX — no toca lógica ni contrato.
**Verificado:** `build`+`lint` verdes.
**Impacto para el otro:** ninguno. Para futuras pantallas: reusar `TableSkeleton`/`EmptyState`/`useToast`.
**Refs:** `frontend/src/components/ui/{Skeleton,EmptyState,Toast}.tsx`, `App.tsx`, pages Dashboard/Pacientes/Recetas, `components/receta/RecetaDetalle.tsx`.

## 2026-07-18 — Fran — frontend (F.3 parcial: dashboard tabla de últimas recetas; cierre mensual bloqueado)
**Qué:** Completé el Dashboard: saludo con el nombre, y la sección "Últimas recetas" pasó de lista simple a
**tabla clickeable** (código/paciente/estado/emitida/vence/total) que abre el detalle de receta (reusa el modal de
F.5) + link "Ver todas" a /recetas. `ultimasRecetas` de `/dashboard/resumen` viene como `RecetaResponse` completa
(verificado) → calculo el total con descuento client-side.
**⚠️ Hallazgo para Santi:** **`GET /dashboard/cierre-mensual?year=&month=` devuelve 500** (tu Fase 1.5). Por eso la
vista de cierre mensual (tasa de conversión + comisión del mes + detalle) **queda pendiente** — no la construí a ciegas.
Ya dejé el type `CierreMensual` en `types/dashboard.ts` listo para cuando el endpoint ande; confirmá el shape.
(Nota: `resumen.ultimasRecetas[].paciente` SÍ trae fechaNacimiento/notas, a diferencia de `GET /pacientes` — otro mapper.)
**Verificado:** `build`+`lint` verdes; tabla contra `/dashboard/resumen` real.
**Refs:** `frontend/src/pages/Dashboard.tsx`, `types/dashboard.ts`.

## 2026-07-18 — Fran — frontend (UI refresh: design tokens + set de íconos)
**Qué:** Refresh visual (más cálido/friendly): nueva paleta de tokens en `index.css` (verde/teal fresco `--primary`
+ acentos amber/blue + neutrales cálidos + sombras + radios), sidebar con gradiente teal e íconos por ítem, topbar
con avatar de iniciales, tiles del dashboard con chips de ícono a color, badges/inputs/botones repulidos (hover,
focus ring, sombras). Nuevo componente **`components/ui/Icon.tsx`** (set SVG inline estilo lucide, **sin dependencia
externa** — respeta la cultura de deps mínimas del proyecto). Login split ya se había hecho aparte.
**Por qué:** pedido de Fran (UI más cozy, íconos, colores). Puramente cosmético — no toca lógica ni contrato.
**Verificado:** `build`+`lint` verdes.
**Impacto para el otro:** ninguno (frontend). Para futuras pantallas: usar `<Icon name=…>` y los tokens CSS existentes.
**Refs:** `frontend/src/index.css`, `components/ui/Icon.tsx`, `components/layout/AppLayout.tsx`, `pages/Dashboard.tsx`.

## 2026-07-18 — Fran — frontend (F.6 parcial: registro público; bandeja admin diferida a Fase 3)
**Qué:** Form **público de registro** de nutricionista (`/registro`, sin auth) → `POST /registro`, con validación
(nombre/apellido/email/teléfono E.164/matrícula/password ≥8) y pantalla de "solicitud enviada, queda PENDIENTE".
Link cruzado Login↔Registro. `pages/Registro.tsx`, `api/registro.ts`, `types/registro.ts`.
**Decisión:** hice **solo la parte pública**. La **bandeja admin de aprobación se difiere a Fase 3** (su fallback en
el plan) porque el backend no está: `GET /admin/nutricionistas` da 500, así que no puedo ni ver el `NutricionistaResponse`
real para tipar la tabla sin adivinar (y el doc ya falló 3 veces esta sesión). Prefiero no construir a ciegas.
**⚠️ Hallazgos para Santi (Fase 1.3, ambos 500):**
1. **`POST /registro` → 500** (`ApiError` "Error interno"). El form está listo y surfacea tu message; cuando implementes
   el alta vía Keycloak Admin API (usuario deshabilitado + `estadoValidacion=PENDIENTE`) funciona sin tocar el front.
   Confirmá el shape de respuesta (asumí `{id, estadoValidacion}`).
2. **`GET /admin/nutricionistas` → 500** (con token ADMIN válido — `admin@nutriapp.dev` tiene `admin:manage`, la auth
   está OK; el endpoint no está implementado). Cuando lo tengas, con el `NutricionistaResponse` real armo la bandeja
   admin (aprobar/rechazar) a la vuelta de vacaciones.
**Verificado:** `build`+`lint` verdes. Happy-path del POST NO verificable hasta tu 1.3 (hoy 500).
**Refs:** `frontend/src/pages/Registro.tsx`, `api/registro.ts`, `types/registro.ts`, link en `pages/Login.tsx`.

## 2026-07-18 — Fran — frontend (F.5: Recetas lista+detalle + 3 hallazgos backend Fase 1)
**Qué:** Pantalla **Recetas** (`/recetas`): lista con filtros (estado/q/desde/hasta) + paginación + detalle en modal
(`GET /recetas/{id}`) con items, notificaciones, conversión y acciones **anular/reenviar** (solo PENDIENTE).
Componentes: `pages/Recetas.tsx`, `components/receta/RecetaDetalle.tsx`, `components/ui/EstadoBadge.tsx` (badge
compartido, refactoricé el Dashboard para usarlo). Helper `fechaHora` en `lib/format`. `api/recetas.ts` +
`listar/get/anular/reenviar`. Borré `pages/Placeholder.tsx` (ya no queda ninguna ruta placeholder).
**Verificado e2e:** `build`+`lint` verdes. Lista + filtro `estado=PENDIENTE` OK contra la seed (2 pendientes).
**⚠️ Hallazgos para Santi (frontend listo contra el contrato; backend pendiente — son tareas de tu Fase 1.1):**
1. **`POST /recetas/{id}/anular` y `/reenviar` devuelven HTTP 500** (`{error:"INTERNAL_ERROR", message:"Error interno"}`).
   Los botones ya están y la UI surfacea tu `ApiError.message` limpio (no muestra "500 pelado"). Cuando implementes
   la lógica, funcionan sin tocar el front.
2. **`GET /recetas/{id}` NO trae `notificaciones` ni `conversion`** (vienen `undefined`), aunque el contrato dice que
   el detalle los incluye. La UI degrada elegante ("Sin notificaciones registradas" / oculta conversión). Al poblarlos
   en el response, se renderizan solos.
3. (menor) El 500 sí viene con el `ApiError` uniforme y `content-type: application/json` → bien, mi client lo parsea.
**Impacto para el otro:** nada rompe; son features tuyas de Fase 1 que el front ya espera. Cuando las cierres, avisá por DIARIO.
**Refs:** `frontend/src/pages/Recetas.tsx`, `components/receta/RecetaDetalle.tsx`, `components/ui/EstadoBadge.tsx`, `api/recetas.ts`.

## 2026-07-18 — Fran — frontend (F.4: Pacientes CRUD + hallazgo backend sobre DELETE)
**Qué:** Pantalla **Pacientes** (`/pacientes`): lista paginada con búsqueda debounced (`GET /pacientes?q=&page=&size=`),
alta/edición en modal (`POST` / `PUT /pacientes/{id}`), baja (`DELETE`, soft). Validación: nombre/apellido/email/whatsapp
obligatorios; **whatsapp en E.164** (`/^\+[1-9]\d{7,14}$/`); email con regex. Componentes: `pages/Pacientes.tsx`,
`components/paciente/PacienteForm.tsx`, `components/ui/Modal.tsx`. Nuevos métodos en `api/pacientes.ts` + request
types `PacienteCreateRequest`/`PacienteUpdateRequest`.
**Verificado e2e:** `build`+`lint` verdes. `POST` 201, `PUT` 200, `DELETE` (sin recetas) 204.
**⚠️ Hallazgos para Santi (2 gaps del backend vs contrato `05-api-endpoints.md`):**
1. **`DELETE /pacientes/{id}` NO valida el 409 "tiene recetas PENDIENTES".** Borré (soft) a Juan Pérez que tenía
   una receta PENDIENTE (`RX-3V737V`) y devolvió **204**, no 409. La UI ya maneja el 409 (surfacea el message) para
   cuando lo implementes, pero hoy no llega. Además queda la receta colgada apuntando a un paciente borrado.
2. **`GET /pacientes` (list y `/{id}`) no devuelve `fechaNacimiento` ni `notas`**, aunque el `POST` sí los acepta y
   echoea. Por eso el form de edición no puede precargarlos → en edición solo expongo los 4 campos que round-trippean
   (nombre/apellido/email/whatsapp); fecha/notas se capturan solo en el alta. Si agregás esos campos al response mapper,
   habilito editarlos.
**Nota:** durante la verificación quedó ensuciada la seed dev (Juan Pérez soft-deleted + 1 receta de prueba). Se limpia
con `docker compose down -v && docker compose up -d db keycloak backend` (reset total + re-seed).
**Refs:** `frontend/src/pages/Pacientes.tsx`, `components/paciente/PacienteForm.tsx`, `components/ui/Modal.tsx`, `api/pacientes.ts`, `types/paciente.ts`.

## 2026-07-18 — Fran — frontend (F.2: Emitir Receta — pantalla core, verificada e2e)
**Qué:** Construida la pantalla **Emitir Receta** (`/recetas/nueva`, la más compleja del MVP):
- Picker de paciente (búsqueda debounced → `GET /pacientes?q=`), buscador de productos (texto libre `q` +
  dropdowns marca/laboratorio/presentación desde `GET /productos/filtros`), selección N items con cantidad +
  indicaciones (UI permite varios; arranca en 1 como pide CLAUDE.md), input de descuento, resumen
  subtotal/descuento/total, `POST /recetas` → pantalla de éxito con el código `RX-XXXXXX`.
- Nuevos types espejo (verificados contra el backend real, no solo el doc): `paciente`, `producto`
  (+`ProductoFiltros`/`ProductoQuery`), `receta` (request+response). Módulos `api/pacientes|productos|recetas`.
  Helpers `hooks/useDebounce`, `lib/format` (money/fecha). Refactoricé el `money` inline del Dashboard a `lib/format`.
**Por qué:** F.2 del sprint pre-vacaciones — el corazón del producto.
**Problemas:** deltas doc↔backend real: `/pacientes` no devuelve fechaNacimiento/notas/contadores en el list;
  `/productos/filtros` NO trae lista de principioActivo (solo marcas/laboratorios/presentaciones) → principio
  activo queda como texto libre vía `q`. Ajusté los types a la realidad.
**Verificado e2e:** `build`+`lint` verdes; `POST /recetas` con el shape exacto que manda la UI → **HTTP 201**,
  `RX-3V737V`, PENDIENTE, vence a 30 días, `cuponSyncEstado=PENDIENTE` (degradación del stub OK), items con
  cantidad+indicaciones. (Quedó 1 receta de prueba extra en el seed dev — inocua.)
**Impacto para el otro:** ninguno para Santi (todo dentro de `frontend/`, consumo el contrato tal cual).
**Refs:** `frontend/src/pages/EmitirReceta.tsx`, `components/receta/*`, `api/{pacientes,productos,recetas}.ts`, `types/{paciente,producto,receta}.ts`.

## 2026-07-18 — Fran — infra (⚠️ PARA SANTI: varios archivos con CRLF rompen el stack en Windows — falta .gitattributes)
**Qué:** Al levantar el stack en la máquina de Fran (Windows, `git core.autocrlf=true`), **3 archivos** quedaron
con line endings **CRLF** al checkoutear y rompen el build/arranque en los contenedores Linux:
1. `backend/mvnw` → `./mvnw: not found` (exit 127): el `#!/bin/sh\r` hace que Linux busque el intérprete `/bin/sh\r`.
2. `backend/.mvn/wrapper/maven-wrapper.properties` → `HTTP 400` al bajar el wrapper: el `\r` final se cuela en la
   `wrapperUrl` y la URL de descarga queda malformada.
3. `db/init/01-keycloak-db.sh` (lo ejecuta Postgres en el init para crear la DB de Keycloak) → CRLF ahí cascadea en
   "el realm nutriapp no existe". Lo normalicé preventivamente.
   (`backend/Dockerfile` también tiene CRLF pero BuildKit lo parsea igual — no lo toqué.)
**Fix aplicado (local, autorizado por Fran):** convertí esos 3 archivos a LF localmente (`sed -i 's/\r$//'`) solo
para desbloquear esta máquina. **NO commiteo archivos de tu área** — los cambios quedan locales.
**Fix durable pendiente (tu área, Santi):** falta un `.gitattributes` en la raíz que fuerce LF en scripts/infra.
  Recomendado:
```
* text=auto eol=lf
mvnw        text eol=lf
*.sh        text eol=lf
*.properties text eol=lf
*.cmd       text eol=crlf
*.bat       text eol=crlf
```
  Y re-normalizar (`git add --renormalize .`). Sin esto, cualquier dev en Windows con autocrlf=true se topa con lo mismo.
  Ojo: con autocrlf=true, git puede volver a mostrar estos archivos como modificados tras un checkout.
**Impacto para el otro:** vos (Santi) probablemente tenés `autocrlf=false`, por eso te buildeó sin problema. En una
  máquina limpia Windows revienta. Es 100% tuyo el fix definitivo (`.gitattributes` es infra/raíz).
**Refs:** `backend/mvnw`, `backend/.mvn/wrapper/maven-wrapper.properties`, `db/init/01-keycloak-db.sh`, falta `.gitattributes` (raíz).

## 2026-07-18 — Fran — frontend (F.1: scaffolding SPA + auth ROPC + layout + routing)
**Qué:** Scaffoldeado `frontend/` desde cero (Vite + React 19 + TS + react-router v7). Plumbing base:
- `config.ts` (lee `VITE_*`; base API = `${VITE_API_BASE_URL}/api/v1`), `api/client.ts` (fetch nativo, Bearer,
  parseo `ApiError`→`message`, 401→clearSession), `lib/auth.ts` (ROPC contra client público `nutriapp-frontend`
  + refresh + tokens en localStorage), `hooks/useFetch.ts` (loading/error/data + AbortController), `auth/AuthContext`.
- Layout Sidebar/Topbar + `RequireAuth` (sin sesión → `<Navigate to="/">`, no PKCE). Routing: `/` login público,
  rutas protegidas `/dashboard` (real, pega a `GET /dashboard/resumen`) + placeholders receta/pacientes.
- `types/` espejo del contrato: `common` (`PageResponse`/`ApiError`), `session` (`Me`), `dashboard`. `.env.example` + `.env.local`.
**Por qué:** F.1 del sprint pre-vacaciones (`04-plan-de-fases.md`). Desbloquea F.2 Emitir Receta.
**Problemas:** CORS — el back fija `http://localhost:5173`; Vite servido en `127.0.0.1` daría origin distinto y
  rompería CORS. Fijé `server.host=localhost` + `strictPort`. `build`+`lint`+`dev` verdes; login e2e sin verificar
  (Docker estaba abajo al scaffoldear).
**Impacto para el otro (Santi):** nuevo dir `frontend/` (mi propiedad). El front asume base `/api/v1` y CORS
  `localhost:5173`. Si el backend NO sirve bajo `/api/v1`, avisá por DIARIO. Sigo consumiendo el contrato de `05-api-endpoints.md`.
**Refs:** `frontend/**` (src/api, src/lib, src/auth, src/hooks, src/components/layout, src/pages, src/types).

## 2026-07-17 — Santi — infra+backend (scaffolding Fase 0: stack levanta + backend seeded, verificado e2e)
**Qué:** Scaffolding completo de Fase 0, portado de imedba y verificado contra el stack real:
- **Infra**: `docker-compose.yml` (db + keycloak + backend; frontend comentado hasta que Fran lo scaffoldee),
  `.env` + `.env.example`, `.gitignore`, `db/init/01-keycloak-db.sh`, realm `nutriapp` con clients
  `nutriapp-frontend` (public+DAG) / `nutriapp-backend` (confidential) + roles ADMIN/NUTRICIONISTA + usuarios
  dev `admin@nutriapp.dev` / `nutri@nutriapp.dev` (pass `test1234`).
- **Backend** (`com.nutriapp`): `pom.xml` (Boot 3.3.5, JPA, Flyway, MapStruct, Lombok, oauth2-resource-server,
  springdoc, testcontainers), Dockerfile multi-stage, `common/` (BaseEntity/PageResponse/ApiError/
  GlobalExceptionHandler/AuthUtils/JwtAuditorAware), `config/` (Security doble-namespace, Cors, OpenApi, JpaAuditing),
  `integrations/` (ports + stubs de tiendanube/contabilium/mail/whatsapp + `IntegrationsConfig` que elige adapter por
  `mode=stub|live`), módulos `nutricionista/paciente/producto/receta/dashboard` + `MeController`.
- **Migraciones** V001 (extensiones + trigger updated_at), V002 (7 tablas núcleo), V003 (seed 12 productos).
  `DevDataSeeder` (@Profile dev, idempotente): 1 nutricionista aprobado + 4 pacientes + 6 recetas en estados variados.
- **Endpoints Fase 0**: `GET /me`, `GET /productos` (+filtros +unaccent), `GET/POST/PUT/DELETE /pacientes`,
  `GET/POST /recetas` (emisión con código único + degradación de cupón), `GET /dashboard/resumen`.
**Por qué:** desbloquear a Fran: contrato REST vivo + backend seeded contra el que construye su sprint antes de irse.
**Decisión:** por indicación del usuario, **por ahora un solo usuario** → el seed crea 1 nutricionista, no 2.
**Problemas (2 bugs de runtime que el compile no atrapó, ambos verificados y resueltos):**
1. **Colisión de bean `mailSender`**: mi `@Bean MailSender mailSender()` chocaba con el `mailSender` (JavaMailSender)
   que autoconfigura `spring-boot-starter-mail`. Renombrado a `@Bean("nutriappMailSender")`. Además el health
   indicator de mail marcaba `/actuator/health` DOWN al no haber SMTP → `management.health.mail.enabled=false` (stub hasta Fase 2).
2. **Token sin claim `sub`**: en Keycloak 25 el `sub` lo aporta el client scope `basic` (mapper `oidc-sub-mapper`).
   Al declarar `clientScopes`/`defaultDefaultClientScopes` explícitos sin `basic`, el access token salía sin `sub`
   → el linkeo nutricionista↔Keycloak fallaba y todo endpoint con scoping tiraba 404. **Fix**: agregué el scope `basic`
   al realm + lo incluí en los default scopes; además hice `NutricionistaService.findCurrent()` robusto (linkea por
   email aunque falte el sub). **OJO para el realm de prod / futuros realms: incluir siempre el scope `basic`.**
**Verificado e2e** (stack real, backend en :8088 porque el 8080 lo ocupa GIA): token ROPC con `sub` → `/me` (estado
APROBADA, 9 authorities) → productos 12 → filtros → pacientes 4 → dashboard (pend 2/aplic 2/venc 1/comisión $5593) →
**POST paciente + POST receta**: código `RX-XXXXXX` generado, estado PENDIENTE, `cuponSync=PENDIENTE` (degradación del
stub OK), vencimiento a 30 días, snapshot de precio. Build en contenedor Maven JDK21 (host tiene Java 17).
**Impacto para el otro (Fran):** el backend levanta con `docker compose up -d db keycloak backend`. En la máquina de
Santi el backend queda en **:8088** (por eso el `.env` local tiene `BACKEND_PORT=8088` y `VITE_API_BASE_URL=...:8088`);
en una máquina limpia el default del compose es 8080. Login ROPC contra `nutriapp-frontend`, realm `nutriapp`,
usuario `nutri@nutriapp.dev` / `test1234`. Contrato en `05-api-endpoints.md`. **Pendiente Fase 1** (Santi, sin bloquear
a Fran): notificaciones (cola+dispatcher), webhook TiendaNube, scheduler de vencimiento, anular/reenviar, registro público.
**Refs:** `backend/**`, `docker-compose.yml`, `keycloak/realms/nutriapp-realm.json`, migraciones `V001-V003`.

## 2026-07-17 — Santi — docs (kickoff: alcance, arquitectura y plan de fases)
**Qué:** Proyecto inicializado. Investigadas las APIs de Contabilium y TiendaNube, mapeada la arquitectura
de imedba (que replicamos acá: mismo stack, mismo docker, mismo sistema de coordinación de dos Claudes) y
escritas todas las docs de diseño: `CLAUDE.md`, `01-arquitectura-sistema.md`, `02-entidad-relacion.md`,
`03-integraciones-apis.md`, `04-plan-de-fases.md`, `05-api-endpoints.md`.
**Por qué:** Presupuesto aprobado por Gon (ver `presupuesto_nutriapp.pdf` + mail de requerimientos).
Fran se va de vacaciones el miércoles 22-07 por 3 semanas → la Fase 0 prioriza dejarle el contrato API y
un backend seeded contra el que pueda construir las pantallas core antes de irse.
**Impacto para el otro:** Fran: leé `04-plan-de-fases.md` §Fase 0 — tus tareas están ordenadas por prioridad
para tu sprint de 3 días. El contrato REST que tenés que consumir está en `05-api-endpoints.md`.
**Refs:** `instrucciones_claude/*.md`, `presupuesto_nutriapp.pdf`.
