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

**Fase actual:** Fase 0 — Cimientos (17–21 jul). **Scaffolding Fase 0 cerrado y verificado e2e (2026-07-17).**

**En qué estoy ahora:**
- **Backend seeded arriba y andando** contra el stack real. Todo lo mínimo de Fase 0 hecho: infra (docker-compose,
  realm `nutriapp`, .env), esqueleto Spring Boot portado de imedba, migraciones V001–V003 + `DevDataSeeder`,
  y endpoints `GET /me`, `/productos` (+filtros), CRUD `/pacientes`, `GET/POST /recetas`, `GET /dashboard/resumen`.
- Integraciones como ports+stubs (`mode=stub`); emitir receta degrada el cupón a PENDIENTE sin romper. Detalle
  + los 2 bugs de runtime resueltos (bean `mailSender`, claim `sub`/scope `basic` de Keycloak) en DIARIO 2026-07-17.
- **Por ahora un solo usuario** (indicación del cliente): el seed crea 1 nutricionista (`nutri@nutriapp.dev`).

**Próximo paso (Fase 1, no bloquea a Fran):**
- Notificaciones (cola + dispatcher + templates), webhook TiendaNube (HMAC + idempotencia + polling respaldo),
  scheduler de vencimiento (diario + catch-up al startup), anular/reenviar receta, registro público + validación admin,
  clientes HTTP reales de las 4 integraciones (aún en stub). Tests unit + Testcontainers.

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
