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

## 2026-07-18 — Fran — frontend (polish round 2: skeletons + empty states + toasts + anim modal)
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
