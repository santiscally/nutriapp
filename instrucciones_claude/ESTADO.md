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

**✅ El hallazgo de seguridad está CERRADO** (venía abierto desde el 2026-09-07). Contraseña del admin
rotada (la vieja `test1234` verificada como rechazada), `admin@nutriapp.dev` renombrado a
`admin@bonosapp.com.ar`, y borradas las dos cuentas basura: `nutri@nutriapp.dev` (huérfana: usuario de
Keycloak sin fila en `nutricionistas`, por eso tiraba "no tiene perfil de nutricionista") y
`test-403@example.com`. **Padrón final, 3 usuarios:** `admin@bonosapp.com.ar` ·
`nutricionista@bonosapp.com.ar` (genérica para el cliente, `APROBADA` y activa) ·
`franallende2000@gmail.com` (Fran, intacta). Las contraseñas no están en el repo.

**Lo que falta — todo cuelga de una sola cosa, la key de Resend:** 🔴 **`MAIL_MODE` sigue en `stub`** con
**4 notificaciones encoladas**; aprobar un registro no avisa a nadie. Y cuando se conecte hay que
arreglar en el mismo movimiento dos cosas del `.env` del VPS: **`ADMIN_NOTIFICATION_EMAIL` no existe**
(el aviso de registro nuevo no llegaría a nadie igual) y **`MAIL_FROM_ADDRESS` sigue en
`no-reply@nutriappok.com.ar`** (dominio viejo → Resend rechazaría los envíos; el verificado es
`bonosapp.com.ar`).

**Otros pendientes:** import del maestro de artículos — **lo hace el cliente** desde la UI; hasta
entonces `sinMaestro=2277` y los filtros de taxonomía quedan vacíos · emisión de un bono e2e contra la
tienda real, sin correr porque crea un cupón de verdad · verificación **visual** del login nuevo · la
decisión abierta de si un producto sin mapear sigue siendo recetable.

## Fran / frontend

**Última actualización: 2026-09-19 (2).**

**En qué estoy:** tanda de **modificaciones post 1ª entrega** (PLAN en
`modificaciones post primera entrega/PLAN-modificaciones-post-entrega.md`).

**✅ Cerrado en esta sesión (front):** F-01 (copy del login) · F-02/03/04 (validaciones del registro: teléfono,
matrícula, CUIT y DNI solo numéricos) · F-05 (jurisdicción = desplegable de provincias + CABA) · F-08/F-09
(renames a "Profesionales" y rutas `/profesionales`, `/bonos`, `/bonos/nuevo`, con redirect desde las viejas) ·
F-11 (mail de contacto en Perfil) · F-12 (estados en masculino, solo label) · F-15 (descarga del PDF del bono) ·
F-23 (el error viejo de E-MAIL ya no parece una falla vigente).

**✅ Cerrado en esta sesión (vertical mail, backend):** F-17 (la URL de tienda ya es config, nada hardcodeado) ·
F-18 **parcial** (link de cupón `<store>/discount/<codigo>` + la frase del cliente) · F-19 (descripción del
producto en mail y wa.me) · F-21 (`GET /api/v1/recetas/{id}/pdf`) · F-22 (asunto del mail sin gancho comercial) ·
F-20 **parcial** (módulo `modules/bonopdf/` con port + generador provisorio sin dependencias; el PDF abre y
extrae texto OK).

**Verificación:** front `tsc -b` + `vite build` + `oxlint` verdes. Backend **207 tests, 0 fallos**. Ojo: **no hay
Java en este host**, se compila y testea en contenedor:
`docker run --rm -v "<repo>/backend:/app" -v bonosapp-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn test`
(en Git Bash, con `MSYS_NO_PATHCONV=1` y la ruta en formato Windows).

**Pendiente mío, sin bloqueo:** nada urgente — lo que queda de mi mitad depende de Santi o del cliente.

**Bloqueado por Santi (contract-first):** F-06 (S-11, campo Profesión) · F-07 (S-16, URL de términos) · F-10
(S-12, `comisionPct` en `/me`) · F-13/F-14 (S-02, descuento por producto) · F-16 (S-07, flag del cupón) ·
F-18 *la mitad que falta* (S-02, URL del producto para linkear directo a la ficha) · F-24/F-25 (S-13/S-14,
endpoints de las solapas admin PANEL y BONOS).

**Bloqueado por terceros:** F-20 — el template del PDF lo manda el cliente la semana que viene. Además, adjuntar
el PDF al mail necesita que `integrations/mail/MailSender` sepa adjuntar, y esa carpeta es **zona de Santi**:
lo dejé sin tocar y avisado en el DIARIO.

**Para Santi, en una línea:** endpoint nuevo `GET /api/v1/recetas/{id}/pdf` sin documentar en
`05-api-endpoints.md` (tu zona, no lo toqué).
