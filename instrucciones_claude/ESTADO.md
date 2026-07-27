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

**Fase actual:** Fase 1 — Backend completo con stubs (21 jul – 8 ago, Santi solo). **Núcleo + webhook (1.4) + clientes HTTP reales (1.6) cerrados; suite 56/56.**

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

**Próximo paso:**
- **Rediseño de UI (prioridad #1)** — en espera de la decisión de coordinación con Fran (arriba). Sistema de diseño ya
  extraído (verde `#0f8a66`/`#16302c`, Comic Neue, top navbar). Ver `04-plan-de-fases.md` §"Rediseño de UI".
- **1.7** Suite formal (integration Testcontainers del flujo emisión→webhook→APLICADA→cierre); hoy hay unit + smoke e2e.
- **1.8** CI GitHub Actions (backend + tsc/lint/build front).

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
