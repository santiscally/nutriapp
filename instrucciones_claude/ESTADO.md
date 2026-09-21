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

**Última actualización: 2026-09-21 (5)** — arrancó la tanda de **modificaciones post 1ª entrega**. Confirmé
el reparto del `PLAN-modificaciones-post-entrega.md` (era una propuesta de Fran) y escribí los
**contratos de las 7 features cruzadas** en `05-api-endpoints.md` → **Fran quedó desbloqueado** en
F-06, F-07, F-10, F-13, F-17, F-18, F-24 y F-25.

**Implementado en esta tanda (sin desplegar todavía):**
- **S-01/S-02 — descuento por producto.** `V015` agrega `descuento_pct`, `estado_bonosapp` y
  `tiendanube_handle` a `productos`. El parser del maestro lee `DESCUENTO %` (fracción → escala
  0-100) y `ESTADO BONOSAPP`. `ProductoResponse` suma `descuentoPct` y `urlProducto`; el buscador
  filtra por `descuentoPct` y `/productos/filtros` devuelve los valores que existen. Al emitir, el %
  sale del producto y cae al de la profesional si el maestro no lo trae.
- **S-11 — profesión + jurisdicción.** `V014` agrega las dos columnas y la tabla `profesiones` con
  las 76 del Excel; `GET /profesiones` es público. Los campos entran **opcionales**: prod recibe
  registros y exigirlos antes de que Fran despliegue rompería el alta.
- **S-12 — comisión.** Default de alta 10 % → **1 %**; `/me` ahora expone `comisionPct`.
- **S-04 — catálogo.** `CATALOGO_TIPOS_ERP` pasa a `Producto` (fuera los Combo).
- **F-04 (la mitad que era backend):** el error del CUIT ya no muestra guiones.

- **S-13/S-14 — las dos solapas del admin.** `GET /admin/dashboard/resumen` y `/estadisticas`
  (consolidado de todas + facturado + padrón) y `GET /admin/recetas` (todos los bonos, con filtro por
  profesional). De paso quedaron implementados los filtros `q`/`pacienteId`/`desde`/`hasta` que el
  contrato prometía en `GET /recetas` y el backend nunca había tenido.

- **S-07 — el cupón deja de ser combinable.** `combines_with_other_discounts` no viajaba y la API lo
  asume `true`: **todos los bonos emitidos hasta hoy se combinan con las promos de la tienda.** Ahora
  viaja explícito, con default `false` y elegible por bono (`combinable` en `POST /recetas`). `V016`.
- **S-16 — términos de uso.** `static/terminos.html` (generado del .docx del cliente) servido por
  nginx en `https://bonosapp.com.ar/terminos`. Verificado sirviendo la página en un nginx local.

- **S-08/S-09 — auth.** Bloqueo temporal tras 10 intentos fallidos y
  `POST /api/v1/password/recuperar` (204 siempre, link de un solo uso emitido por Keycloak). La
  config del realm la aplica `scripts/keycloak-config.sh`, **paso nuevo y obligatorio del deploy**:
  el realm JSON sólo se importa la primera vez, así que sin el script prod se queda con el default
  de 30 intentos y sin SMTP. Verificado sobre el stack: bloqueo real, mail entregado y link abierto.

- **S-10 — verificación de mail.** En paralelo a la aprobación del admin: el alta sigue quedando
  `PENDIENTE`, pero sin validar la casilla no se entra. Reenvío público y `emailVerificado` en la
  bandeja. El script hace el backfill de `emailVerified` **antes** de exigirla, si no el padrón
  entero queda afuera en el próximo login.
- **UI de "olvidé mi contraseña"** (`/recuperar-password` + link en el login). Es **lo único que
  toqué en `frontend/`**, por pedido explícito, acotado para no pisar a Fran.

**🔴 Lo que falta:** **S-03** — el filtro de RUBRO que deja pasar cajas de cartón. Necesita mirar datos
de prod: hipótesis, `rubro_id` viene null desde `/api/conceptos/search` y `permitido()` deja pasar lo
ausente. Sin arrancar: S-05, S-06, S-15, S-17, S-18.

**⚠️ Antes de desplegar esto a prod:** correr `bash scripts/keycloak-config.sh`, `V014`/`V015`/`V016`, re-sincronizar el catálogo (cambiar
`CATALOGO_TIPOS_ERP` no recalcula nada por sí solo) y correr el mapeo de TiendaNube para que se
pueble el `handle` de cada producto — sin eso `urlProducto` viaja en null y el link del mail no sale.
El `.env` del VPS necesita además `CATALOGO_TIPOS_ERP=Producto` y `TIENDANUBE_STORE_URL=https://www.thebcompany.com.ar` (S-17).

**⚠️ Dos preguntas abiertas para Gon, antes de importar el maestro nuevo:** `DESCUENTO %` viene como
`0.2`/`0.55` sin formato de porcentaje (se lee 20 % y 55 %), y `ESTADO` vs `ESTADO BONOSAPP` se
contradicen — **1497 de 2252 filas están BLOQUEADO** y las 2252 están en `SI`. Detalle en el PLAN,
sección "Cambios al alcance".

**Estado de producción (no romper):** `bonosapp.com.ar` en vivo sin pre-lanzamiento · **mail live**
(Resend, `info@bonosapp.com.ar`; el TXT DKIM `resend._domainkey` no se toca) · Contabilium y
TiendaNube **live** contra la tienda real de TBC — emitir un bono crea un cupón de verdad · padrón
limpio de 3 usuarios (`admin@bonosapp.com.ar`, `nutricionista@bonosapp.com.ar`, Fran) · el hallazgo
de seguridad de la seed de dev quedó **cerrado** el 16/09 · el maestro de artículos **lo importa el
cliente** desde la UI y todavía no lo hizo (`sinMaestro=2277`).

## Fran / frontend

**Última actualización: 2026-09-21 (2).**

**En qué estoy:** modificaciones post 1ª entrega (PLAN en
`modificaciones post primera entrega/PLAN-modificaciones-post-entrega.md`). **Mi mitad está cerrada salvo lo
que depende de terceros.**

**✅ Cerrado (todo en `main`, pusheado):**
- **1ª tanda:** F-01..F-05, F-08, F-09, F-11, F-12, F-15, F-17, F-19, F-21, F-22, F-23.
- **2ª tanda (destrabada por S-01..S-16):** F-06 (profesión desde `GET /profesiones`), F-07 (link a
  `/terminos`), F-10 ("Tu comisión" en el perfil), F-13 (filtro % + % por producto), F-16 (checkbox
  combinable destildado + cálculo con el descuento del producto), F-18 **completo**, F-24 (solapa PANEL),
  F-25 (solapa BONOS).

**Verificación:** backend **239 tests, 0 fallos** (en contenedor: no hay Java en este host). Front `tsc -b` +
`oxlint` + `vite build`. Y las dos pantallas nuevas del admin **verificadas en el navegador** contra el backend
local (Chrome headless por CDP): login → `/panel` y `/admin/bonos` con datos reales, sin errores de consola.

**⚠️ Lo que aprendí probando F-18 (importa para el cliente):** TiendaNube **ignora** los parámetros de redirect
en `/discount/<codigo>` — siempre cae en la home. No existe un link único que aplique el cupón *y* aterrice en
el producto, así que el mensaje manda **dos links en orden**: primero el que activa el bono, después el del
producto. Si Gon esperaba un solo link, esto hay que contárselo.

**Bloqueado, y no por Santi:**
- **F-20** — la plantilla del PDF la manda el cliente. Además, adjuntarlo al mail necesita que
  `integrations/mail/MailSender` sepa adjuntar, y esa carpeta es zona de Santi.
- **F-14** — hasta que el cliente importe el maestro nuevo, el % de la ficha del admin es el único descuento
  que existe (lo marcó Santi en el contrato de S-02).

**Notas de entorno (mi máquina):**
- `frontend/.env.local` apuntaba al realm viejo `nutriapp`: corregido a `bonosapp`. Si el login local falla con
  "No pudimos conectarnos con el servidor", mirar ahí primero.
- Mi `.env` de raíz tiene `MAIL_MODE=live`: **levantar el stack local con `MAIL_MODE=stub` por variable de
  entorno**, o el dispatcher manda mails reales por Resend desde la máquina.
- Backend sin Java en el host:
  `docker run --rm -v "<repo>/backend:/app" -v bonosapp-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn test`
  (Git Bash: `MSYS_NO_PATHCONV=1` y la ruta en formato Windows).

**Esperando de Santi:** documentar `GET /api/v1/recetas/{id}/pdf` en `05-api-endpoints.md` (su zona) y el
adjunto en `MailSender` para F-20.
