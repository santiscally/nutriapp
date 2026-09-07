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

**Última actualización: 2026-08-25** — 📧 **El mail funciona de verdad** (y TiendaNube quedó cerrado contra la
tienda demo, ver más abajo). Suite **192/192**.

**Mail — era la última integración en stub y nunca se había ejecutado.** El stub tiraba excepción antes de
tocar nada, así que `SmtpMailSender` jamás había enviado un mensaje. Ahora se ejercita en local con **Mailpit**
(`docker compose --profile mail up -d`, webmail en **http://localhost:8026**; 8025 lo tiene otro proyecto).
Verificado: 13 notificaciones viejas drenaron `QUEUED→SENT`, y el flujo de **registro** manda acuse + aviso al
admin, y la **aprobación** manda el mail de cuenta activa. Acentos correctos (verificado sobre el `.eml` crudo).
`MAIL_SMTP_AUTH` y `MAIL_SMTP_STARTTLS` ahora son env (antes hardcodeadas en `true`). **Para 2.3 sólo falta que
Gon elija proveedor**: son env vars sobre un camino ya probado.

**TiendaNube — cerrado contra la demo.** App 40301 en `thebcompanydemo.mitiendanube.com` (store **8145981**),
todos los scopes. Cupón real emitido y verificado, con **dos productos** y restringido correctamente. Dos bugs
que sólo aparecieron pegándole a la API real: **`coupons.products[]` lleva PRODUCT id, no VARIANT id** (con
variant → 422, ningún cupón se habría creado nunca) y **colección vacía = 404**, no array vacío (el polling
habría logueado ERROR cada 5 min). Guard nuevo: si un producto de la receta no está mapeado, **no se llama a la
API** y el cupón queda PENDIENTE con el motivo — sin eso salía un cupón **sin restricción = descuento a toda la
tienda**. Endpoints admin nuevos: `mapear-productos` y `registrar-webhooks` (ambos idempotentes).

**Bonos sin cantidades** (decisión del usuario): el cupón de TiendaNube no sabe de unidades, así que
`cantidad` quedó topeada en 1 (`@Max(1)`) y el emisor perdió el input. Se recetan N productos, uno de cada uno.

**Otros:** `HttpMediaTypeNotSupportedException` → **415** (antes `/registro`, endpoint público, devolvía 500).
Emoji fuera del mensaje de WhatsApp + **link a la tienda** en el WhatsApp y en el mail (`TIENDANUBE_STORE_URL`).
Frontend (con permiso explícito del usuario, ver DIARIO): acciones con íconos en Pacientes y Bonos, columna
"Acciones" alineada, notas del paciente en modal.

**Falta para la tienda del cliente (2.5):** instalar la app en TBC → nuevo store_id/token, correr el mapeo
(mirar `skusSinMatch` / `pendientes`) y registrar el webhook. En local quedan **691 publicados sin mapear**:
no están en la demo.

**Decisión abierta (2 veces planteada, sin respuesta):** un producto sin mapear hoy sigue siendo recetable y la
receta sale con un cupón que nunca se crea. ¿Se sacan del buscador hasta mapearse? Son ~691 de un saque.

**Sin commitear**: todo en el working tree.

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
