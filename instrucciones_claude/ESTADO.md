# ESTADO — Snapshot de trabajo en curso

> **Qué es esto.** Foto corta y actualizable de **en qué está cada uno ahora mismo**. No es historia
> (eso va en `DIARIO.md`), es el presente.
>
> **Regla de uso para el Claude activo:**
> - **Solo tocar la sección del dueño activo.** Santi edita "Santi / backend", Fran edita "Fran / frontend".
>   Nunca tocar la sección del otro (evita merge conflicts).
> - **Sobreescribir, no appendear.** Esta es una foto, no un log.
> - Actualizar al **empezar** una tarea nueva y al **terminarla**.
> - Si algo está **bloqueado esperando al otro**, dejarlo explícito en la sub-sección "Bloqueado por el otro".

---

## Santi / backend / infra / db / auth

**Última actualización: 2026-08-03** — Ola 3 (maestro de artículos) hecha del lado backend; ver el bloque
🆕 más abajo. Documento nuevo: `07-maestro-articulos-y-catalogo.md`.

**Fase actual:** Fase 1 — Backend completo con stubs. **✅ COMPLETA (1.1–1.8), 2026-07-27.** Núcleo + webhook (1.4) + clientes HTTP reales (1.6) + estadísticas + config de negocio por admin + **integración Testcontainers (1.7)** + **CI (1.8)**. Arrancada Fase 2: **resiliencia 2.7–2.9 en stub (2026-07-27)**. `mvn verify` = **72 unit + 1 IT, BUILD SUCCESS**.

**🔔 DEMO CON EL CLIENTE HECHA (2026-07-31, Gon + Leo) — hay backlog nuevo.** La plataforma les gustó
("espectacular", tipografía Comic Neue incluida). Salieron **17 cambios** (16 de la call + la foto de perfil de la nutricionista, C-17), ordenados en 4 olas en
**`06-cambios-post-demo-2026-07-31.md`** (cada ítem con timestamp de la call). Los tres que más pegan:
(1) la comisión se calcula **solo** sobre el total real de TiendaNube, con descuentos **acumulables** (15%+30%=45%);
(2) **los precios salen de casi toda la app** — solo se ven en el buscador de la emisión, con leyenda de "aproximado";
(3) aparece la **liquidación**: estado terminal `LIQUIDADA` + cierre consolidado del admin con exportable.
Además el **admin deja de poder emitir recetas**. Transcripción completa en `transcripcion-2026-07-31-call-gon-leo.pdf|.txt`.
**Segundo tema de la call — proyecto nuevo, alcance confirmado:** un **data warehouse de toda la data de Contabilium**
(productos, ventas, comprobantes, clientes, stock, compras) con **sync diario automático**, para **dejar de pegarle a
las APIs** — las apps leen del warehouse, nutriapp incluido. Insumos + relevamiento completo de la API oficial
(entidades, rate limits, matemática de requests, arquitectura, fases, riesgos) en `../../datawarehouse-contabilium/docs/nuevo-proyecto-tbc-insumos.pdf` (proyecto aparte, fuera de este repo).
Sin cotizar todavía; falta el detalle (cuánta historia, qué reportes, quién lo usa) porque los 9 min donde se habló
no quedaron transcriptos.

**Resiliencia 2.7–2.9 (scaffold en stub, 2026-07-27):** 3 endpoints admin nuevos + jobs que degradan con mensaje explícito
y se encienden al pasar a `live` en Fase 2. **2.7:** `GET /admin/integraciones/estado` (por proveedor: modo/disponible/
pendientes/últimoError/últimaSync) + `RecetaResponse.cuponSyncMensaje` (nullable, aditivo) + 503 con texto por proveedor.
**2.8:** `CuponSyncService.registrar()` (extraído de `emitir`, compartido) + `CuponSyncJob` (@Scheduled) + `POST /admin/tiendanube/resync-cupones`.
**2.9:** `ProductoSyncService` (conciliación por SKU + `last_synced_at`) + `POST /admin/contabilium/sync-productos` (stub → 503).
`IntegrationHealthRegistry` in-memory para disponible/últimoError. +11 tests. Sin migración. **Front: panel `/integraciones`
(solo admin) HECHO** — card por proveedor con badges modo/disponible/pendientes + botones reintentar-cupones / sincronizar-catálogo
(build+lint OK). Detalle en DIARIO.

**1.7/1.8 (2026-07-27):** `RecetaFlowIT` (Testcontainers Postgres, flujo emisión→webhook→APLICADA→cierre) vía failsafe (`mvn verify`; `mvn test` sigue sin Docker). CI `.github/workflows/ci.yml` (backend `sh mvnw verify` + frontend tsc/lint/build, push/PR a main). `.gitattributes` nuevo (EOL LF para scripts, cierra el pendiente de Fran).

**Parámetros de negocio configurables por admin (2026-07-27):** `modules/configuracion/` — descuento (fijo global) y comisión editables en runtime (`GET /configuracion`, `PUT /admin/configuracion`, tabla `configuracion_sistema` seed 15/10). `RecetaService`/webhook leen de ahí; `RecetaCreateRequest` ya no lleva `descuentoPct`. Front: pantalla `/configuracion` (solo admin) + `EmitirReceta` lo muestra read-only. Cierra preguntas abiertas #1/#2 (valor exacto TBD con Gon).

**⚠️ Prioridad #1 — rediseño de UI COMPLETO (2026-07-27, R.1–R.7):** todas las pantallas alineadas al mockup
(`instrucciones_claude/Diseño gestor recetas nutricionista/`). Usuario autorizó implementarlo **sobre main** (Comic Neue).
**Pendiente único:** verificación visual e2e con el stack corriendo. Se commitea todo a main. **Hecho y verificado (build+lint OK):** tokens del
sistema (`index.css`), **shell sidebar→top navbar + footer** (`AppLayout`+`Footer`), tiles reestilados, **página nueva
`/cierre-mensual`** (endpoint real), Dashboard con header/acciones/4º tile + **gráficos reales** (barras recetas/mes +
tendencia comisión) contra el **nuevo `GET /dashboard/estadisticas`** (backend, suite 58/58), y **Login / Registro /
RecetaEmitida** alineadas al mockup (split-screen). **Falta:** verificación visual e2e + fine-tuning opcional de
Emitir/Recetas/Pacientes (ya heredan tokens+navbar; consistentes). Detalle R.1–R.7 en `04-plan-de-fases.md`.
**Ojo Fran:** el layout y los tokens cambiaron; NO toqué tu sección de este ESTADO (regla de propiedad).

**En qué estoy ahora:**
- **Fase 1 núcleo hecha y verificada** contra el stack real. Todo lo que Fran flaggeó en 500 anda:
  módulo `notificacion` (cola + `NotificacionDispatcher` scheduled + templates), `emitir` encola EMAIL+WHATSAPP,
  `RecetaResponse.notificaciones`, `POST /recetas/{id}/anular` + `/reenviar` (guards de estado → 409),
  `DELETE /pacientes/{id}` → 409 si hay PENDIENTES, `RecetaVencimientoJob` (cron diario + catch-up al startup),
  `GET /dashboard/cierre-mensual`, y **registro + admin vía Keycloak Admin API** (`POST /registro` público crea
  usuario deshabilitado + PENDIENTE; `GET/POST /admin/nutricionistas` aprobar/rechazar habilita/deshabilita en KC).
- **Webhook TiendaNube (1.4) hecho** (`modules/webhook/`): `POST /webhooks/tiendanube` (HMAC hex tiempo-constante +
  `webhook_events` idempotente + 200 inmediato), procesamiento async (`getOrder` fuera de tx → matcheo cupón → **APLICADA**
  + comisión), polling de respaldo (24h), y **simulador de dev** (`POST /api/v1/dev/tiendanube/orden-pagada`, `@Profile("dev")`)
  para llegar a APLICADA en stub → habilita la demo con Gon. En stub degrada sin romper (evento queda para reintento).
- **Clientes HTTP reales (1.6) hechos** (`integrations/*/Http*|Smtp*|CloudApi*`): Contabilium (token 24h + throttle 15/10s),
  TiendaNube (UA + backoff 429, 4 métodos), SMTP mail, WhatsApp Cloud API. Se registran solo con `mode=live`; testeados con
  WireMock (`wiremock-standalone` test dep). **Conectar de verdad es Fase 2** (credenciales de Gon).
- Integraciones externas siguen en `mode=stub` (degradan sin romper). Keycloak Admin es always-live (nuestro IdP).
- Backend local en **:8088**, Keycloak :8081, Postgres :5432. Login dev `nutri@nutriapp.dev` / `test1234` (y `admin@nutriapp.dev`).
- **Review pasado** (code + security): fixes aplicados + **suite de tests unitarios (38, `mvn test`)** + smokes e2e
  (`smoke-fase1.sh` 17/17, `smoke-webhook.sh` 13/13). Hardening de Fase 3 (service-account KC, rate limiting `/registro`
  **y `/webhooks`**) documentado en DIARIO — no bloquea.

**OLA 1 + C-01 EN CURSO (2026-08-01). Hecho y verificado e2e — `mvn test` 97 unit, BUILD SUCCESS:**
- ✅ **C-01 % de descuento y comisión por nutricionista**: migración `V007` (nullable = usa el global),
  `ParametrosNegocioService` como único resolutor override→global, `PUT /admin/nutricionistas/{id}/parametros`.
  El % se snapshotea en la receta al emitir/convertir. `0%` es override válido; sólo `null` cae al global.
- ✅ **C-05 estado `LIQUIDADA`**: migración `V006` + `LiquidacionService` idempotente +
  `POST /api/v1/admin/liquidaciones` (`admin:manage`, 403 para nutricionista). Los cierres cuentan
  `APLICADA` **e** `LIQUIDADA` (liquidar no deshace la conversión); `findLiquidables` filtra sólo las impagas.
- ✅ **C-04 cierre por fecha de pago real** (`ordenPaidAt`, no `aplicadaAt` ni emisión).
- ✅ **C-03 verificado**: la comisión ya salía del total real de TiendaNube. Sin cambios.
- ✅ **C-14 inactivos de Contabilium** no se publican (estado desconocido → se asume activo, defensivo).
- 🐛 **2 bugs preexistentes del working tree, arreglados de paso**: NPE que tumbaba la sync entera de catálogo
  (`Map.of().get(null)` con conceptos sin rubro) y un test con fixture desactualizado. Detalle en DIARIO.

**⚠️ Contrato cambiado — Fran tiene que espejar:** `RecetaResponse.Conversion.liquidadaAt` (nullable),
`CierreMensualResponse.Detalle.liquidadaAt` (nullable), `EstadoReceta` suma `"LIQUIDADA"` (el front tiene 4 valores),
y la fila de la bandeja de admin suma `descuentoPct`/`comisionPct` (override, **campo ausente si es null**) +
`descuentoPctEfectivo`/`comisionPctEfectiva` (ya resueltos contra el global).

- ✅ **C-07 admin sin recetas (2026-08-02)**: el rol realm ADMIN dejó de arrastrar `recetas:*`/`pacientes:*`/
  `productos:read`/`dashboard:read` → **403 real**, no sólo menú oculto (aplicado en el realm JSON **y** por
  `kcadm` sobre el Keycloak vivo). Front: nav por rol, CTA oculto, `RequireRol` por ruta, landing por rol.
- ✅ **C-02 precios sólo en emisión (2026-08-02)**: `RecetaResponse.Item` sin `precioLista`; sin importes en
  detalle, éxito y dashboard (ahí ahora se muestra la **venta real** de TiendaNube, "—" si no convirtió);
  leyenda de "valores aproximados" + "Total estimado" en el carrito.

**⚠️ Falta verificación visual** de C-07 y C-02: está todo verificado por contrato (API, typecheck, build) pero
nadie miró las pantallas todavía. Stack arriba: back `:8088`, front `:5173`, usuarios `nutri@`/`admin@nutriapp.dev`.

- ✅ **C-09 bandeja de nutricionistas (2026-08-02)**: `/nutricionistas` sólo admin, tabs pendientes/aceptadas/
  rechazadas, ficha con aprobar/rechazar + set de los % de C-01 (vacío = global). **La casa del admin ahora es
  esta pantalla.** Verificado e2e: registro → pendiente → login bloqueado → aprobar con 30%/8% → login OK.
- ✅ **Mensajes de login traducidos**: `Account disabled` (el caso más común: cuenta pendiente de aprobación),
  brute-force y error de red ya no se muestran en inglés ni como `Failed to fetch`.

- ✅ **C-06 cierre consolidado del admin (2026-08-02)**: `GET /admin/liquidaciones/consolidado?desde&hasta`
  (rango configurable, fechas inclusive, sin paginar) + pantalla `/cierres` con tiles, botón "Liquidar N" por
  nutricionista y **exportable CSV** listo para Excel es-AR (`;` + BOM + decimales con coma, `lib/csv.ts`).
  Verificado e2e: $2.050 pendientes en 2 recetas → liquidar → histórico intacto, pendiente 0, reintento idempotente.
  **Falta la columna CUIT del exportable** (la pidió Gon): el campo no existe hasta C-08.

- ✅ **C-08 + C-17 (2026-08-02)**: subsistema de archivos (`nutricionista_archivos`, bytes en la DB para que
  entren en el backup existente, whitelist de content-type, uno vigente por tipo). Registro **multipart** con
  DNI/CUIT/condición fiscal + adjunto de matrícula (el admin lo abre desde la ficha); **el CUIT ya sale en el
  exportable de C-06**. Foto de perfil en `/perfil`, redimensionada en el server a 256px y servida como data
  URI dentro de `/me` (220 KB → 14 KB en la prueba). `mvn test` **113 unit**.
  - **Dos bugs reales encontrados y arreglados**: `@Lob byte[]` no mapea a `BYTEA` en Hibernate 6 (va
    `@JdbcTypeCode(SqlTypes.VARBINARY)`), y la compensación de Keycloak del registro no cubría fallas en el
    **commit** → usuario huérfano. Patrón: forzar `saveAndFlush` dentro del bloque protegido.

**OLA 1 y OLA 2 CERRADAS** (C-01…C-09, C-14, C-17). **Ola 4** (TiendaNube real) necesita las credenciales
del Partner Portal. Pendiente menor: confirmar con Leo si el campo `matricula` va como "matrícula nacional"
(call 42:34).

**🆕 OLA 3 CERRADA (2026-08-03), back y front.** Llegó el Excel maestro de Gon → C-10, C-11, C-12 y C-13
hechos, más los 3 ajustes nuevos que vinieron en el mismo mail (código de barras, `Tipo`, rubro).
`mvn verify` **141 unit + 1 IT BUILD SUCCESS**, migración `V009`, front en verde.
(De paso: `RecetaFlowIT` estaba roto desde el 2026-07-28 — dependía del seed de `V003` que se vació ese
día. Ahora crea su propio producto.)
**Análisis, decisiones y plan completos en `07-maestro-articulos-y-catalogo.md`.**

- **Importador C-12**: `POST /admin/productos/importar-maestro` (multipart) + `GET /admin/productos/maestro/estado`.
  Parser por **nombre de columna** (nunca por posición), `fastexcel-reader`. Verificado con el archivo real:
  **2163 de 2225 filas aplicadas, 62 sin match, 0 rechazadas**, 26 despublicados, 8447 tags.
- **Dos escritores sin campos compartidos** (Contabilium vs maestro) → importar y sincronizar son
  conmutativos; `publicado` es derivado (`PublicacionPolicy`). Verificado: el sync corrido después del
  import no pisó nada del maestro.
- **`categoria` cambió de significado**: antes era el Rubro de Contabilium ("Producto terminado" para el
  99,8 %), ahora es la del maestro (23 valores). El rubro se mudó a `rubro`/`rubro_id`.
- **Buscador C-10 rankeado**: nombre → descripción → tag, y `q` también matchea código de barras.
  **C-11**: filtros de departamento/categoría/subcategoría/laboratorio + `taxonomia` en cascada.

- **Frontend hecho** (`tsc`/`oxlint`/`build` verdes): card de import en `/integraciones` (examinar +
  reporte; **avisa como advertencia, no como éxito, si hubo filas sin match**), filtros en cascada en el
  buscador, miniatura por fila y modal "Más info" con imagen, datos, texto largo y tags clickeables
  (click en tag = filtrar por tag). La imagen es un link al CDN de TiendaNube: no cuesta storage, pero
  solo la tiene el 24 % de lo recetable.

**⚠️ A consultar con Gon (ya decidido de nuestro lado):** el `Tipo` de Contabilium tiene **tres** valores
(Producto 2004 / **Combo 209** / Servicio 54). "Solo quedarnos con Producto", tal como lo pidió, sacaba
también los 209 combos (packs y exhibidores ON-ROLL) → el catálogo caía de 702 a 496. **Se dejaron los dos**
(`CATALOGO_TIPOS_ERP=Producto,Combo`, default en compose y `.env.example`) → **699 recetables, 203 combos**.
Volver a su versión literal es cambiar el env var y re-sincronizar.

- **CORS para `:5174` + registro en una pantalla (2026-08-03):** levantar el front en 5174 rompía login y
  registro. Hay que tocar **tres** lugares: `app.cors.allowed-origins`, el **`.env` local** (pisa el default
  del compose) y los **`redirectUris` del client `nutriapp-frontend` en Keycloak** (tiene `webOrigins:["+"]`,
  los orígenes salen de ahí). Los dos puertos quedaron permitidos en todos lados **y aplicados al Keycloak
  vivo** por Admin REST API. El registro (11 campos desde C-08) pasó a card de 720px con grilla de 3
  columnas + compactación por alto de viewport, y se emparejó la altura de inputs/selects/file inputs.

**⚠️ Falta verificación visual** de las pantallas nuevas y del registro: todo verde por contrato, nadie las
miró. Stack arriba: back `:8088`, **front `:5174`** (el 5173 lo ocupa imedba en esta máquina).

**⚠️ Contrato — Fran tiene que espejar (todo aditivo, nada se rompe):** `ProductoResponse` suma
`codigoBarras`, `descripcionWeb`, `departamento`, `subcategoria`, `tags[]`; `ProductoFiltrosResponse` suma
`departamentos`, `subcategorias`, `laboratorios`, `taxonomia`. **`categoria` cambió de datos, no de forma.**
`principioActivo` y `presentacion` siguen existiendo pero son siempre `null` (no existen en ninguna fuente —
esa búsqueda la cubren los tags).

**Usuarios de prueba:** `nutri@nutriapp.dev` (nutricionista, % global), `admin@nutriapp.dev` (admin),
`ana.test@nutriapp.dev` (nutricionista aprobada con 30%/8%, creada verificando C-09). Todos con `test1234`.
Olas 3 y 4 bloqueadas (Excel maestro + credenciales de TiendaNube).

**Estado previo (Fase 1 CERRADA — Fase 2 / pendientes menores):**
- **Verificación visual e2e del rediseño** (único pendiente del rediseño; no bloquea): levantar stack y revisar en vivo.
- **Fase 2 — integraciones reales** (necesita a Gon: credenciales + tienda demo TiendaNube + proveedor mail/WhatsApp).
  Resiliencia 2.7–2.9 **ya hecha en stub** (backend + panel front `/integraciones`) — al conectar los clientes reales
  drena lo acumulado sin tocar código.
- **✅ Contabilium CONECTADO LIVE contra prod (2026-07-28):** credenciales del `.env` validadas (J&L NEO PHARMA SAS,
  2266 productos / 50 páginas). **Fix de charset UTF-8** en `HttpContabiliumClient` (venían mojibake los nombres con Ñ).
  Probe read-only dev nuevo (`GET /dev/contabilium/probe`). **`CONTABILIUM_MODE=live` persistido en `.env`** (machine-local).
  **Catálogo SIN seed:** `V003` vaciado → arranca en 0 productos; se puebla con el botón "Sincronizar catálogo" (`/integraciones`,
  admin) → sync real. **Stack reconstruido** (`down -v`+`up --build`): 0 productos verificado, backend live, front `:5173` arriba.
  **TiendaNube sigue en stub.** Detalle en DIARIO.
- **✅ Mejoras de catálogo (2026-07-28):** filtros **reales** (marca←Subrubro, categoría←Rubro de Contabilium + "solo con stock";
  fuera laboratorio/presentación que no existen), **paginador** en el emisor, **sync asíncrono** (202 + polling + toasts), **recetas
  hasta 10 productos** (`RECETA_MAX_ITEMS`), y **footer "powered by `<s/a>`"** (Simple Apps). Migración `V005` (columna `categoria`).
  **Falta re-sincronizar** para poblar categoria/marca en los 2266 (el sync por SKU los actualiza). Contrato de `/productos` cambió
  (params + `/filtros`); type espejo del front actualizado. Verificado: back compila (main+tests), front build verde, endpoints OK.
- **Hardening Fase 3 EN CURSO:** ✅ rate-limiting `/registro` y `/webhooks` (token bucket por IP, 429+Retry-After, `mvn verify` 84 unit+1 IT). ✅ **Keycloak Admin por service-account** (`client_credentials` de `nutriapp-backend`, roles `realm-management` `manage-users`+`view-users`+`view-realm` — sale el superuser del master; verificado registro→aprobar en vivo). ✅ **`docker-compose.prod.yml` + nginx TLS + backup/restore (2026-07-28):** nginx único servicio público (80/443), reverse proxy single-domain (`/`→SPA, `/api/`→backend, `/auth/`→Keycloak) + security headers + rate-limit de red + CSP; **bring-your-own-cert** (`nginx/certs/`, git-ignored) + `scripts/gen-selfsigned-cert.sh`; Keycloak modo prod bajo `/auth`; secretos fail-closed; `scripts/{backup,restore}-db.sh`; runbook `DEPLOY.md`. YAML validado; boot real = paso de deploy (Docker + dominio). **Sigue (ops + al confirmar hosting con Gon):** regenerar el secret del client en el realm de prod + Let's Encrypt/renovación. Todo committeado local (branch main, 15+ commits adelante de origin, **sin push**).

**Bloqueado por el otro:** nada.

**Notas para Fran:**
- El stack levanta con `docker compose up -d db keycloak backend`. **En la máquina de Santi el backend queda en
  `:8088`** (el 8080 lo ocupa plataforma GIA) — en una máquina limpia el default del compose es `:8080`. Ajustá
  `VITE_API_BASE_URL` según tu `.env`.
- Login: ROPC contra client `nutriapp-frontend`, realm `nutriapp`, `http://localhost:8081`. Usuario de prueba:
  `nutri@nutriapp.dev` / `test1234` (rol NUTRICIONISTA, ya aprobado, con 4 pacientes + 6 recetas seedeadas).
- Contrato REST en `05-api-endpoints.md` (congelado durante tus vacaciones). NO hay mocks: todo va al backend real.
- Tu plan de sprint está en `04-plan-de-fases.md` §Fase 0, ordenado por prioridad (F.1 auth+layout → F.2 Emitir Receta
  → F.3 Dashboard → F.4 Pacientes → F.5 Recetas → F.6 Registro).

---

## Fran / frontend

**Fase actual:** Fase 0 — Cimientos (17–21 jul). **Me voy de vacaciones el 22-07, vuelvo ~12-08.**

**Sprint pre-vacaciones cerrado (2026-07-18). `frontend/` en verde: `build`+`lint`+`dev` OK.** Detalle por feature en el DIARIO.

**Hecho y verificado e2e contra el backend real:**
- **F.1** SPA (Vite+React19+TS+router v7): `api/client.ts` (Bearer + `ApiError`→message), `lib/auth.ts` (ROPC+refresh),
  `useFetch`, `useDebounce`, `AuthContext`, layout Sidebar/Topbar/RequireAuth, `lib/format`, `components/ui` (Modal, EstadoBadge).
- **F.2 Emitir Receta** (picker paciente + buscador productos con filtros + N items + descuento + resumen → `POST` 201 → éxito con código).
- **F.4 Pacientes** (lista+búsqueda+paginación + alta/edición modal con validación E.164 + baja soft). POST/PUT/DELETE OK.
- **F.5 Recetas** (lista+filtros+paginación + detalle modal + anular/reenviar). Lista OK; acciones dependen del backend (abajo).
- **F.6 (público)** form de registro `/registro` (validado, con pantalla de "pendiente de aprobación").

**Pendiente para la vuelta (~12-08):**
- **F.3** Dashboard: tabla de últimas recetas clickeable → detalle **HECHA**. Falta solo el drill-in de cierre mensual
  (`GET /dashboard/cierre-mensual` da **500** — Fase 1.5 de Santi). Type `CierreMensual` ya listo para cuando ande.
- **UI**: refresh visual aplicado (paleta cálida + íconos `components/ui/Icon` + Emitir Receta a 2 columnas con resumen sticky).
- **F.6 admin**: bandeja de aprobación (`GET /admin/nutricionistas` + aprobar/rechazar). **Diferida a Fase 3** porque el backend da 500 y no pude tipar `NutricionistaResponse` sin adivinar.
- Cuando Santi cierre su Fase 1, re-verificar: anular/reenviar receta, notificaciones/conversion en el detalle, `POST /registro`.

**Notas infra (mi máquina):**
- Para levantar el stack: parar contenedores `imedba-*` (ocupan 8080/8081/5432/5173) → `docker compose up -d db keycloak backend`.
- Arreglé CRLF local en `backend/mvnw`, `maven-wrapper.properties`, `db/init/01-keycloak-db.sh` (**pendiente `.gitattributes` de Santi** — ver DIARIO).
- `frontend/.env.local` apunta a `:8080`. Reset de seed: `docker compose down -v && up`.

**Bloqueado por el otro:** para completar F.5 acciones / F.6 hace falta la Fase 1 de Santi (varios endpoints hoy en 500). Nada bloquea el núcleo ya entregado.
