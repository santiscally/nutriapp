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

**Última actualización: 2026-09-07** — 🏷️ **Rebranding NutriApp → BonosApp** hecho y commiteado. Suite
**199/199**. Se cerró además todo lo que venía sin commitear del 2026-08-25.

**Rebranding (pedido del cliente, 2026-09-03).** Muere NutriApp, nace **BonosApp**, dominio
**bonosapp.com.ar**. El **isotipo no cambia** (es el mismo de Gon: lo verifiqué contra el lockup nuevo),
así que favicon y apple-touch-icon quedaron intactos; cambia el wordmark. Kit oficial en `brand/`.
`og-image.png` regenerada con el lockup real sobre el verde de marca. La landing y el registro muestran
la casilla de contacto que pidió el cliente, desde **`VITE_CONTACTO_EMAIL`** (default
`info@nutriappok.com.ar` — la casilla nueva no existe todavía). **El rename es sólo de cara al usuario:**
paquete `com.nutriapp`, realm y clients de Keycloak, red, contenedores, DBs y el nombre del repo siguen
igual A PROPÓSITO (ver `CLAUDE.md`).

**Falta para que el dominio nuevo esté en vivo (ops, no código)** — checklist completo en `DEPLOY.md`,
sección "Migración a bonosapp.com.ar":
1. Importar `bonosapp.com.ar.zone` en hPanel (verificar el par de NS **de ese** dominio, y que no quede
   un `A` de parking).
2. Site block en el Caddy del VPS + `redir` permanente desde `nutriappok.com.ar`.
3. Rebuild de la SPA con `VITE_API_BASE_URL` / `VITE_KEYCLOAK_URL` en `https://bonosapp.com.ar` (se
   hornean en el bundle; con el origen viejo la CSP bloquea los fetch).
4. **Agregar `https://bonosapp.com.ar/*` a los redirect URIs del client `nutriapp-frontend` en el
   Keycloak de prod**, o el login rompe con `invalid_redirect_uri`.
5. `APP_PUBLIC_URL=https://bonosapp.com.ar` en el `.env` del VPS + rebuild del backend (los textos de
   los mails viajan en el jar).
6. Avisarle a Leo cuando esté, para que mude la casilla de contacto.

**Backend que se commiteó junto (venía del 2026-08-25):** notificaciones de registro (enum
`TipoNotificacion`, migración **V013**, templates de recibido/aprobado/rechazado + aviso al admin,
dispatcher con batch y reintentos), `PublicacionPolicy` con las 5 reglas de qué producto es recetable y
el motivo en castellano, `TiendaNubeMapeoService` + `TiendaNubeWebhookRegistrar` (ambos idempotentes,
estado en `/integraciones/estado`), y handlers 415/400/422 en `GlobalExceptionHandler`.

**Falta para la tienda del cliente (2.5):** instalar la app en TBC → nuevo store_id/token, correr el
mapeo (mirar `skusSinMatch` / `pendientes`) y registrar el webhook. En local quedan **691 publicados sin
mapear**: no están en la demo.

**Decisión abierta (3 veces planteada, sin respuesta):** un producto sin mapear hoy sigue siendo
recetable y el bono sale con un cupón que nunca se crea. ¿Se sacan del buscador hasta mapearse? Son ~691
de un saque.

**Mail:** para 2.3 sólo falta que Gon elija proveedor — son env vars sobre un camino ya probado con
Mailpit (`docker compose --profile mail up -d`).

## Fran / frontend

**Última actualización: 2026-08-13** — de vuelta de vacaciones y sincronizado con el pull.

**Contexto:** en mis 3 semanas Santi avanzó muchísimo (tocó `frontend/` con mi permiso, avisado en DIARIO): la app
está **EN PROD** (`nutriappok.com.ar`, pre-lanzamiento), con Fase 1, Fase 2 (Contabilium live; TiendaNube/email en
stub; WhatsApp por `wa.me`) y las **4 olas post-demo** hechas. El front del repo ya refleja todo eso (coming-soon,
taxonomía/catálogo admin, %-por-nutricionista, liquidación, archivos DNI/CUIT/matrícula, foto de perfil, precios
fuera de casi toda la app, admin sin emisión). Todo mi sprint pre-vacaciones (F.1–F.6) quedó absorbido y superado.

**Entorno local puesto a punto (2026-08-13):**
- Puertos alineados con Santi para esquivar imedba/GIA: `.env` local con `BACKEND_PORT=8088`, front `:5174`
  (`VITE_DEV_PORT`), keycloak `:8081`. `vite.config.ts` ahora lee el puerto de env.
- Dropeé mis 3 fixes CRLF locales (`.gitattributes` de Santi ya cubre mvnw/*.sh). **Queda un hueco:**
  `maven-wrapper.properties` no está cubierto → arreglo local + flag a Santi en DIARIO.
- Stack local levantado (back `:8088`, keycloak `:8081`, db `:5432`) + front `:5174`. Contabilium en stub →
  **catálogo local vacío** (se puebla sólo con credenciales + "Sincronizar catálogo").

**En qué estoy ahora / próximo:**
- **Verificación visual** de las pantallas nuevas que Santi dejó marcadas como "falta mirar" (C-02 precios, C-07
  admin sin emisión, registro de 11 campos, catálogo, rediseño R.1–R.7).
- **Funcionalidad pendiente de email del registro** (bloqueante funcional: proveedor mail en stub → nadie recibe
  el aviso de "solicitud recibida/aprobada").

**Bloqueado por el otro:** nada.
