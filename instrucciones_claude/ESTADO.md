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

**Última actualización: 2026-09-16** — 🚀 **PRE-LANZAMIENTO APAGADO.** `https://bonosapp.com.ar`
dejó de mostrar la landing "Próximamente" y muestra el **login**. La propuesta de valor de la landing
(headline, lead, los 3 pasos y la casilla de contacto) se mudó al **panel izquierdo del login**;
`Proximamente.tsx` queda en el repo detrás de `config.comingSoon` por si hay que reencenderlo.

**Qué se tocó:** `frontend/src/pages/Login.tsx` + `frontend/src/index.css` (variante
`.auth__steps--icon`) — `frontend/` es de Fran, se tocó **por pedido explícito del usuario** y está
avisado en el DIARIO. En el `.env` del VPS: `VITE_COMING_SOON=false` y `VITE_CONTACTO_EMAIL` a
`info@bonosapp.com.ar`. En `.env.example` (raíz): contacto, el comentario de CORS y el de
`KEYCLOAK_ISSUER_URI` (los dos comentarios que habían inducido bugs reales en prod).

**Deploy hecho y verificado contra el dominio público:** build en `node:22-alpine` (el VPS no tiene
node), `tsc -b` verde, bundle `index-D_7wzobQ.js` servido por bind mount sin reload · `/` e
`/ingresar` 200 · flags horneados OK · CSP/HSTS intactos · **login ROPC con `Origin` → token**, y con
él `/api/v1/me` y `/api/v1/admin/nutricionistas` **200**. Backups: `frontend/dist-old-20260916-*` y
`.env.bak-comingsoff-*`.

**🔴 LO ÚNICO URGENTE — sigue abierto y ahora pesa más.** La credencial **seed de dev**
`admin@nutriapp.dev` funciona en producción con `ADMIN` + `admin:manage`, y su contraseña está en el
realm JSON versionado en el repo. Con el login como **home pública**, esto ya no es teórico:
**rotarla o borrar la cuenta ahora**; ídem `nutri@nutriapp.dev`. No la toqué: son las únicas cuentas
admin y la decisión es del usuario.

**CATÁLOGO EN PRODUCCIÓN, LISTO PARA USAR** (2026-09-16). Contabilium y TiendaNube en `live` contra la
tienda **real** (`bienestarandsalud.mitiendanube.com`, `store_id` 4135704). Sync → **2277 productos**;
mapeo por SKU → **609 de 614, 0 sin match**; **578 publicados (recetables)**. Webhook `order/paid`
registrado y verificado. ⚠️ Emitir un bono ahora crea un **cupón real** en la tienda del cliente.

**Lo que falta:** 🔴 **Resend / `MAIL_*`** — el mail sigue en `stub` con **3 notificaciones encoladas**;
es el único hueco funcional (aprobar un registro no avisa a nadie) · **import del maestro de artículos**:
lo hace el cliente desde la UI, hasta entonces los filtros de departamento/categoría/subcategoría/
laboratorio quedan vacíos (`sinMaestro=2277`) · **emisión de un bono e2e contra la tienda real**, sin
correr todavía porque crea un cupón de verdad · verificación **visual** del login nuevo · 🔴 rotar la
credencial seed `admin@nutriapp.dev`, que ahora es la home pública.

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
