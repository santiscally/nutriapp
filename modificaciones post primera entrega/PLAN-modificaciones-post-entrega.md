# Modificaciones post primera entrega — Plan y división de trabajo

> **Origen:** feedback del cliente (Gon) tras la 1ª entrega — dos mails casi idénticos (un solo set de
> requerimientos) + 4 adjuntos en esta carpeta.
> **Fecha:** 2026-09-19.
> **Objetivo de este doc:** repartir las tareas entre **Fran** y **Santi** de forma **pareja en esfuerzo**
> y, sobre todo, **sin pisarse** — que cada uno pueda avanzar autónomo a su ritmo.
> **Estado:** **CONFIRMADO por Santi el 2026-09-21** — el reparto queda como está. Los contratos de
> las 7 features cruzadas ya están escritos en `instrucciones_claude/05-api-endpoints.md`, sección
> "Modificaciones post 1ª entrega": Fran puede arrancar F-06/F-07/F-10/F-13/F-17/F-18/F-24/F-25 contra
> ese shape sin esperar al backend. Ajustes de Santi sobre su mitad, abajo en "Cambios al alcance".

## Principio de reparto

- **Mail y todo lo que dispara/arma el mail o el WhatsApp → Fran** (es su vertical, ya venía con el agente
  de notificaciones). Incluye el `frontend/` completo.
- **Servicios de Hostinger, DNS, servidor/VPS, Keycloak, catálogo/ERP e integraciones → Santi.**
- Lo que no cae claro en ninguno se reparte por área para equilibrar y evitar colisiones.

## Zonas de propiedad (para NO pisarse)

| Zona | Dueño |
|---|---|
| Todo `frontend/` | **Fran** |
| Vertical mail en backend: `modules/notificacion/**`, `modules/receta/service/WaMeLinkBuilder.java`, nuevo módulo de PDF del bono | **Fran** |
| Resto de `backend/` (catálogo `modules/producto/**`, `integrations/**`, `modules/receta` salvo WaMeLinkBuilder, admin/agregados, auth) | **Santi** |
| `keycloak/`, `nginx/`, `docker-compose*`, `db/`, `scripts/`, `.env` del VPS, DNS/Hostinger | **Santi** |

**Regla para features compartidas (contract-first):** cuando una tarea de Fran depende de datos del backend,
**Santi define primero el shape del endpoint/campo** (lo deja anotado en `05-api-endpoints.md`), y Fran
construye la UI contra ese contrato. Así nadie espera al otro tocando el mismo archivo.

---

# TAREAS DE FRAN (frontend + vertical mail)

## Frontend — cambios de texto/UI (quick wins, sin dependencias)
- **F-01 · Login:** "BIENVENIDA DE NUEVO" → "BIENVENIDA". "Usuario o email" → solo "Email".
- **F-02 · Registro:** error de Whatsapp/teléfono → "Formato incorrecto" + ejemplo (`Ej: +5491133334444`).
- **F-03 · Registro:** "N° de matrícula" → campo **solo numérico** (no letras ni símbolos).
- **F-04 · Registro:** "CUIT" → **solo numérico** (sin letras ni guiones); el mensaje de error no debe mostrar los `-`.
- **F-05 · Registro:** "Jurisdicción de matrícula" → **desplegable** con Provincias + CABA.
- **F-08 · Rename "Nutricionistas" → "Profesionales":** en TODO el texto visible del front + ruta
  `/nutricionistas` → `/profesionales`. (La ruta del front; el path del API lo ve Santi en S-15 si se quiere renombrar también.)
- **F-09 · Rename URL Bonos:** donde la ruta del front diga `recetas` → `bonoprofesional`/`bonos`.
- **F-11 · Perfil:** en "¿Necesitás corregir tus datos o matrícula?" agregar "escribinos a **info@bonosapp.com.ar**".
- **F-12 · Estados en masculino (solo display):** APLICADA→"Aplicado", VENCIDA→"Vencido", ANULADA→"Anulado",
  PENDIENTE→"Pendiente". **NO tocar el enum `EstadoReceta` ni la DB** — solo el label en la UI (`EstadoBadge`).

## Frontend — dependen de backend de Santi (contract-first)
- **F-06 · Registro:** agregar desplegable "Profesión" (valores de `Profesiones.xlsx`). → depende de **S-11** (campo `profesion` en backend).
- **F-07 · Registro:** link "Términos de uso" bajo la frase de privacidad. → depende de **S-16** (URL donde Santi hostee el término).
- **F-10 · Perfil:** "Descuento de tus bonos" → "**Tu comisión**" + mostrar el % cargado (default 1%). → depende de **S-12** (que `/me` exponga `comisionPct`).
- **F-13 · Emitir bono:** agregar **filtro "% Descuento"** + mostrar el % en la línea del producto (a la izq. del precio) y en "Más info". → depende de **S-02** (descuento por producto en el API).
- **F-14 · Admin, ficha del profesional:** quitar "Descuento de bonos (%)" de Porcentajes (el descuento ahora es por producto, igual para todos). → coordinar con **S-02/S-12**.
- **F-16 · Crear bono:** checkbox "Permitir combinar con otras promociones…" **DESTILDADO** por default. → coordinar con **S-07** (flag del cupón).
- **F-24 · Admin, nueva solapa PANEL:** UI del consolidado que hoy ve el profesional en su PANEL. → depende de **S-13** (endpoint agregado).
- **F-25 · Admin, nueva solapa BONOS:** UI de todos los bonos de todos los profesionales; replicar los filtros del user + agregar filtro **Profesional (nombre y apellido)**. → depende de **S-14** (endpoint).

## Vertical MAIL / WhatsApp / PDF (Fran, backend notificación)
- **F-17 · Mensaje wa.me + mail:** corregir la URL de la tienda → `www.thebcompany.com.ar` (en el mensaje de la webapp y en el mail al paciente). → el valor lo setea Santi en **S-17** (`TIENDANUBE_STORE_URL`); Fran lo consume en los templates.
- **F-18 · Mensaje wa.me + mail:** el hipervínculo debe ir **directo al producto con el descuento aplicado**
  (1 bono = 1 producto; cada producto tiene su URL) + frase: *"Dale click al link y sumá el producto al
  carrito, y automáticamente estará aplicado tu bono (No combinable con promociones activas)"*. Ver
  `Ejemplo de Link…jpeg`. → depende de **S-02** (URL del producto en el API).
- **F-19 · Mensaje wa.me + mail:** incluir la **descripción del producto**. Ej: *"Tu bono profesional de
  {descripción} con {XX}% de descuento ya está listo…"* (webapp y mail).
- **F-20 · Mail:** adjuntar el **bono profesional en PDF**. ⏳ **BLOQUEADO:** el cliente manda el template la
  próxima semana. Se puede ir armando la infra de generación de PDF con un template provisorio.
  **Estado 2026-09-24: ya no está bloqueada.** El template provisorio existe (`PdfSimpleBonoGenerator`) y
  `MailSender` ya adjunta archivos (`MailSender.Adjunto.pdf(nombre, bytes)`). Falta sólo conectar el PDF al
  mail del paciente; cuando llegue el template del cliente se cambia el diseño, no el circuito.
- **F-21 · Backend notificación:** endpoint para **re-descargar el PDF** del bono (alimenta F-15).
- **F-15 · Bono emitido (UI):** ícono para **re-descargar el PDF** del bono. → depende de F-21.
- **F-22 · Deliverability (contenido):** estructurar el mail para no caer en "Promociones" (from-name,
  texto menos "promocional", ratio texto/HTML). → coordina con **S-18** (DNS/DMARC).
- **F-23 · Admin, Integraciones:** revisar el error que muestra "E-MAIL" (ahora que el mail está `live` en
  prod puede haberse limpiado) y ajustar el display si hace falta.

## Agregadas después del plan
- **F-26 · Responsive (celular y tablet)** — sumada el 2026-09-23: la app **no se usa bien en el celular**.
  **Dueño: Santi** (reasignada el 2026-09-24 por decisión de Santi, aunque es `frontend/`). **En curso.**
  - **Lo que ya se hizo** (Santi, commit `088371b`, en `main`): los problemas de *layout* que se pueden medir
    al abrir cada pantalla — login, registro y recupero cortados; tablas con columnas inaccesibles; título
    tapado por la navbar; pie fijo en celular. Medido sobre las 15 pantallas a 375, 768 y 1280 px. El CSS
    está en el último tramo de `index.css`, sin tocar reglas existentes.
  - **Lo que falta para darla por hecha:**
    - **Modales y paneles flotantes**, que no se revisaron: detalle del bono, ficha y porcentajes del
      profesional, "Más info" del producto, panel de filtros del buscador, selector de paciente, avisos.
    - **Emitir bono:** la lista de productos tiene su propio scroll de 340 px adentro del scroll de la
      página; en el celular el dedo mueve una cosa o la otra.
    - **Tablas** (bonos, pacientes, profesionales, catálogo, cierres): hoy se deslizan de costado. En celular
      lo habitual es mostrar cada fila como una tarjeta.
    - **Navegación:** las pestañas se deslizan con un fundido. En celular lo habitual es un menú
      (hamburguesa o barra inferior).
    - **Los flujos completos en un celular**, no sólo cada pantalla por separado: emitir → éxito → mandar
      por WhatsApp; registro con el archivo de la matrícula; cierre mensual.
    - **Safari de iOS:** la navbar queda arriba gracias a `overflow-x: clip`, que pide Safari 16 o más; en
      versiones anteriores se va con el scroll. No rompe nada, pero hay que verlo en un iPhone.

---

# TAREAS DE SANTI (backend + infra/hosting)

## Catálogo / Productos
- **S-01 · Maestro de artículos BonosApp:** definir/actualizar el formato del maestro según
  `Maestro Articulos BonosApp.xlsx`. Incluye columna **"Descuento por producto"** (impacta el bono, distinto
  por producto) y **"Estado Bonosapp"** (SI = aparece en el buscador para emitir bono; NO = no aparece).
  Ajustar el parser del maestro (`MaestroXlsxParser` / `MaestroImportService`).
- **S-02 · Descuento por producto:** modelarlo y **exponerlo en el API de productos** (alimenta F-13 y F-18).
  Reemplaza el descuento por-nutricionista (V011 ya sacó el global; ahora es por producto).
- **S-03 · Filtro RUBRO:** que solo traiga **RUBRO = PRODUCTO TERMINADO (id 144331)** — en prod está trayendo
  hasta cajas de cartón. Revisar por qué no aplica el `CATALOGO_RUBROS_PERMITIDOS` contra Contabilium live.
- **S-04 · Filtro TIPO:** que solo traiga **TIPO = PRODUCTO** (sacar los Combos). `CATALOGO_TIPOS_ERP`:
  `Producto,Combo` → `Producto`.
- **S-05 · Foto de producto rota:** diagnosticar por qué no se ve (imagen_url no poblada desde el ERP / CSP de
  nginx bloqueando el origen externo / etc.). Puede tocar `nginx/` (CSP) = infra.

## Integraciones / Conversión
- **S-06 · Timing conversión → dashboard:** el estado "APLICADO" no viaja rápido después de convertir una
  venta con el cupón. Revisar webhook `order/paid` + polling de respaldo. Validar el **"Cierre de comisiones"**.
- **S-07 · Cupón "no combinable":** que el cupón se cree en TiendaNube **sin** permitir combinar con otras
  promociones (default destildado). → coordina con F-16.

## Auth / Keycloak
- **S-08 · Anti-brute-force:** bloquear la cuenta tras >10 intentos de password incorrectos (brute-force
  detection de Keycloak).
- **S-09 · Recupero de contraseña automático:** flujo de reset por email (hoy no existe por diseño; el cliente
  lo pide para no blanquear a mano).
- **S-10 · Verificación de mail en el registro:** "VALIDÁ TU MAIL" vía link obligatorio (para evitar mails
  inventados). Keycloak email-verify o flujo propio. → coordina con Fran (UI de confirmación + copy del mail).
- **S-11 · Campo "Profesión":** migración + entity + DTO + registro para persistir la profesión (alimenta F-06).

## Admin backend
- **S-12 · Comisión default 1%:** hoy el default es 10% → 1%. Verificar que `/me` exponga `comisionPct` para F-10.
- **S-13 · Endpoint solapa PANEL (admin):** consolidado de lo que ve el profesional en su PANEL (alimenta F-24).
- **S-14 · Endpoint solapa BONOS (admin):** todos los bonos de todos los profesionales, con los filtros del
  user + filtro por Profesional (alimenta F-25).
- **S-15 · (Opcional) Rename API `nutricionistas` → `profesionales`:** solo si se quiere alinear el path del
  endpoint con la ruta del front (F-08). El texto y la ruta del front no lo necesitan.

## Infra / Hosting / Deliverability
- **S-16 · Hostear "Términos de uso":** publicar el contenido de `BonosApp - Terminos de Uso.docx` en una URL
  (página estática) para linkear desde el registro (alimenta F-07).
- **S-17 · `TIENDANUBE_STORE_URL`:** setear a `https://www.thebcompany.com.ar` en el `.env` del VPS
  (hoy `bienestarandsalud.mitiendanube.com`) — alimenta F-17.
- **S-18 · Deliverability / anti-spam:** que "Recibimos tu solicitud" no caiga en Promociones/Spam (el de
  Rechazo sí llegó a prioritaria). Reputación de dominio + DMARC (hoy `p=none`) + warmup. → coordina con F-22.

---

# Cambios al alcance (Santi, 2026-09-21)

Ajustes sobre la propuesta después de mirar el código y los adjuntos del cliente. Nada se saca del
reparto; se corrigen tres supuestos y aparecen dos decisiones que necesitan al cliente.

## Lo que crece

- **S-11 se lleva también la jurisdicción de matrícula.** Hoy el front manda
  `"{jurisdicción} · N° {matrícula}"` pegado en el campo `matricula` porque no hay columna
  (`Registro.tsx:103`, ya estaba flageado a Santi). La migración de S-11 parte el dato en tres
  columnas —`matricula`, `jurisdiccion`, `profesion`— así F-03 y F-05 quedan limpias en vez de seguir
  concatenando. Las profesiones van a **tabla con seed Flyway + `GET /profesiones` público**: son 76
  valores que manda el cliente en un Excel, no una constante — el front no los hardcodea.
- **S-14 arrastra los filtros de `GET /recetas`.** F-25 pide "replicar los filtros del user", pero el
  backend hoy **solo tiene `estado`**: `q`, `pacienteId`, `desde` y `hasta` estaban documentados en
  `05-api-endpoints.md` y nunca se implementaron. S-14 los hace en los dos endpoints a la vez.
- **Parte de F-04 es de Santi.** El mensaje de error del CUIT que muestra los guiones sale del
  backend (`RegistroRequest`), no del front. Lo cambia Santi.

## Lo que se frena

- **F-14 (sacar "Descuento de bonos (%)" de la ficha del admin) no se puede hacer todavía.** El
  descuento por producto sale del maestro, y el cliente **todavía no lo importó en prod**
  (`sinMaestro=2277`). Hasta que lo haga, el % de la ficha es el único descuento que existe: si Fran
  saca el campo antes, no queda forma de emitir un bono con descuento. Orden correcto: S-01/S-02
  desplegadas → cliente importa el maestro → recién ahí F-14.

## Decisiones que necesitan al cliente (Gon)

1. ~~**`DESCUENTO %` viene como `0.2` y `0.55`, sin formato de porcentaje.**~~ **RESUELTO (2026-09-21):
   son 20 % y 55 %.** El parser acepta igual las dos escalas (por debajo de 1 = fracción, de 1 en
   adelante = porcentaje) para que un 20 tipeado a mano mañana no rompa nada.
2. ~~**`ESTADO` y `ESTADO BONOSAPP` se contradicen en el archivo que mandaron.**~~ **RESUELTO
   (2026-09-23), sin consultar al cliente: manda `ESTADO BONOSAPP`**, que es la columna con la que el
   plan dice que se decide qué aparece en el buscador. `ESTADO` queda como dato. Era la regla que ya
   estaba implementada, así que no hubo que tocar código. (Contexto: en el archivo que mandaron, 1497
   de 2252 filas estaban `BLOQUEADO` en `ESTADO` y todas en `SI` en `ESTADO BONOSAPP`.)

---

# Puntos de coordinación (los únicos con dependencia cruzada)

| Feature | Santi hace (contrato) | Fran hace (UI/mail) |
|---|---|---|
| Descuento por producto | S-02 (dato en el API) | F-13, F-18 |
| Profesión | S-11 (campo) | F-06 (desplegable) |
| Comisión | S-12 (default + `/me`) | F-10 (label + %) |
| Términos de uso | S-16 (hostear URL) | F-07 (link) |
| URL tienda | S-17 (config VPS) | F-17 (templates) |
| Solapa PANEL admin | S-13 (endpoint) | F-24 (UI) |
| Solapa BONOS admin | S-14 (endpoint) | F-25 (UI) |
| Cupón no combinable | S-07 (flag cupón) | F-16 (checkbox) |
| Verificación de mail | S-10 (flujo) | UI + copy |
| Deliverability | S-18 (DNS/DMARC) | F-22 (contenido) |

# Balance de esfuerzo (estimación gruesa)

- **Fran:** muchos cambios chicos de front (F-01→F-16) que suman rápido, **+ el peso está en el vertical mail**
  (rework de templates F-17/18/19, generación de PDF F-20/21, y las 2 solapas nuevas de admin F-24/25).
- **Santi:** menos ítems pero **más pesados** — modelo de descuento por producto + maestro (S-01/02), flujos de
  auth (S-08/09/10), timing de conversión (S-06), y los 2 endpoints agregados (S-13/14).
- Queda **parejo**. Los bloqueos externos: F-20 (template PDF, llega la próxima semana).

# Adjuntos del cliente (en esta carpeta)

- `BonosApp - Terminos de Uso.docx` → S-16 / F-07.
- `Profesiones.xlsx` → F-06 (valores del desplegable).
- `Maestro Articulos BonosApp.xlsx` → S-01 (formato + columnas nuevas).
- `Ejemplo de Link que debe figurar en whatsapp y mail.jpeg` → F-18.

---

# Onboarding para un agente nuevo (arrancar esta tanda)

Si sos un Claude nuevo que agarra estas modificaciones, hacé esto **antes de tocar código**:

1. **Leé, en orden:**
   - `CLAUDE.md` (raíz) — stack, reglas de oro (nada mockeado; stub/live por config), **propiedad del repo**
     (frontend = Fran / backend+infra = Santi), y coordinación entre los dos Claudes.
   - `instrucciones_claude/DIARIO.md` — las últimas ~10 entradas (contexto real de lo ya hecho; **prod está vivo**).
   - **Este PLAN** — tus tareas, con IDs `F-xx` (Fran) / `S-xx` (Santi) y las dependencias cruzadas.
   - `instrucciones_claude/ESTADO.md` — snapshot; **leé tu sección**.
   - `instrucciones_claude/05-api-endpoints.md` — contrato REST. ⚠️ **Puede estar desactualizado**: verificá los
     shapes contra respuestas reales del backend antes de tipear (lección registrada: "contract drift").

2. **Confirmá de qué lado estás** (te lo dice el humano): **Fran** (todo `frontend/` + el vertical mail:
   `modules/notificacion/**`, `WaMeLinkBuilder.java`, PDF del bono) **o Santi** (resto de backend + infra/hosting).
   **No toques la zona del otro** — ver la tabla "Zonas de propiedad". Si una tarea te obliga a cruzar, **parás y avisás**.

3. **Reglas duras (de CLAUDE.md):**
   - Features compartidas = **contract-first**: el dueño del backend define el endpoint/campo primero (lo anota en
     `05-api-endpoints.md`), el del front construye contra eso. Así no se pisan.
   - **No editar migraciones Flyway ya aplicadas** (`V001`–`V013`): cambian el checksum y el backend no arranca.
   - **Estados de bono en masculino = solo label en el front** (F-12). NO tocar el enum `EstadoReceta` ni la DB.
   - Secretos (API keys) **solo en `.env`**, nunca en código/docs/commits. En prod, en el `.env` del VPS a mano.
   - Al **cerrar una tarea no trivial**: entrada en el `DIARIO.md`. Al empezar/terminar: sobreescribí **tu** sección de `ESTADO.md`.
   - Respuestas y commits concisos.

4. **Estado de producción hoy (no romper):** `bonosapp.com.ar` **en vivo, sin pre-lanzamiento**; **mail live**
   (Resend, `info@bonosapp.com.ar` — el TXT DKIM `resend._domainkey` en Hostinger **no se toca**); Contabilium y
   TiendaNube **live** contra la tienda real de TBC (cuidado con pruebas que emitan cupones o toquen stock real);
   padrón de usuarios limpio (`admin@bonosapp.com.ar`, `nutricionista@bonosapp.com.ar`, `franallende2000@gmail.com`).

5. **Bloqueo externo conocido:** F-20 (template del PDF del bono) llega del cliente la próxima semana.

**Prompt de arranque sugerido para pegarle al agente nuevo:**
> Vas a trabajar en las modificaciones post 1ª entrega de BonosApp. Sos [Fran / Santi]. Antes de tocar nada, leé
> `CLAUDE.md`, las últimas 10 entradas de `instrucciones_claude/DIARIO.md`, tu sección de `ESTADO.md` y
> `modificaciones post primera entrega/PLAN-modificaciones-post-entrega.md`. Hacé solo las tareas [F-xx / S-xx] de
> tu lado, respetando las zonas de propiedad y contract-first. No toques la zona del otro sin pedido explícito.
> Prod está vivo: no rompas el mail, el DKIM ni las integraciones live.
