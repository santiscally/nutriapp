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
