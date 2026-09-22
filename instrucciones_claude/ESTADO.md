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

**Última actualización: 2026-09-22 (3)** — mi mitad de las modificaciones post 1ª entrega está **cerrada
salvo lo que necesita producción**. Todo en `main`, **nada desplegado todavía**.

**Hecho y verificado:** S-01/S-02 (maestro nuevo + descuento por producto + link al producto) ·
S-04 (fuera los Combo) · **S-05** (la foto de producto) · S-07 (cupón no combinable) · S-08
(fuerza bruta) · S-09 (recupero de contraseña + su UI) · S-10 (verificación de mail) · S-11
(profesión y jurisdicción) · S-12 (comisión 1 %) · S-13/S-14 (solapas PANEL y BONOS del admin) ·
S-16 (términos de uso en `/terminos`). Más: adjuntos en `MailSender` (destraba F-20 de Fran) y el
endpoint del PDF documentado.

**S-03 cerrado sin tocar código:** el filtro de rubro **funciona** (los 104 productos fuera de
"Producto terminado" están despublicados, y las cajas de cartón están en `Insumos para producción`,
ya excluidas). Lo que falta es **re-sincronizar prod**, que ya está en la checklist. Ojo con el
número: después del re-sync el catálogo recetable ronda los **1015**, no los 2277 — la regla de
precio se lleva 947 (los ~1000 artículos a $1). Para lo que el cliente igual quiera afuera, la
palanca es `ESTADO BONOSAPP`.

**🔴 ABIERTO — RX-R7H85N: bono comprado el viernes, sigue PENDIENTE.** Gon lo reportó el 22/09, o sea
que **en prod sí se están usando bonos**. Ya está el rescate: `POST /admin/tiendanube/reconciliar?horas=720`
aplica los que quedaron colgados (el polling sólo mira 24 h, así que solo no se arreglaba nunca), más un
barrido nocturno de 30 días y `ultimoWebhookAt` en el panel para saber si los webhooks llegan. **La causa
raíz necesita prod**: no la pude ver desde acá porque el `.env` local apunta a otra tienda (8145981 vs
4135704). Pasos de diagnóstico en orden, en el DIARIO del 22/09 (3) — el primero es mirar si la orden
figura como `paid` y no como `pending`/`authorized`.

**S-06 cerrado:** el pipeline estaba bien; lo que fallaba era que `@EnableScheduling` corría con
**un solo hilo** (default de Spring) para los seis jobs, así que el processor de webhooks hacía cola
detrás del polling de TiendaNube y del dispatcher de mails. `spring.task.scheduling.pool.size` = 4,
con un test que lo cuida.

**🔴 Lo que falta, todo del lado de producción:**
- **S-17** — `TIENDANUBE_STORE_URL` en el `.env` del VPS. (Fran verificó que la tienda ya redirige
  `bienestarandsalud.mitiendanube.com` → `www.thebcompany.com.ar` con un 301, así que no es urgente.)
- **S-18** — deliverability: DMARC está en `p=none` y los mails caen en Promociones.
- **S-15** — rename del path del API. Opcional, sin hacer.

**⚠️ Checklist de deploy (6 pasos, ninguno hecho):**
1. `bash scripts/keycloak-config.sh` — fuerza bruta + SMTP del realm + verificación de mail, con el
   backfill de `emailVerified` **antes** de exigirla. Sin este paso, el realm de prod se queda con el
   default de 30 intentos, sin mail de recupero, y activar la verificación a mano dejaría a todo el
   padrón afuera.
2. Migraciones `V014` / `V015` / `V016`.
3. Re-sincronizar el catálogo (cambiar `CATALOGO_TIPOS_ERP` no recalcula nada por sí solo).
4. **Correr el mapeo de TiendaNube**: puebla el `handle` (sin eso `urlProducto` viaja en null y el
   link del mail no sale) **y ahora también la foto de cada producto**.
5. `TIENDANUBE_STORE_URL` en el `.env` del VPS.
6. Montar `./static` en nginx (términos de uso) y recargar la config (el CSP cambió: ahora deja pasar
   las imágenes de la tienda).

**Después del deploy, dos cosas:** abrir un link real de recupero de contraseña para confirmar que el
`KEYCLOAK_HOSTNAME` arma bien el enlace (mismo tipo de bug que el `issuer` sin `/auth` de agosto), y
recién ahí pasar `profesion` / `jurisdiccion` / `matricula` a obligatorios en el backend — el front de
Fran ya los manda los tres, pero exigirlos antes del deploy rompe el alta si alguien tiene el front
viejo cacheado.

**⚠️ Para preguntarle a Gon antes de importar el maestro:** `ESTADO` y `ESTADO BONOSAPP` se
contradicen — **1497 de 2252 filas están BLOQUEADO** y a la vez las 2252 están en `SI`. Implementado
queda que manda `ESTADO BONOSAPP`. Y ojo: **2078 de 2252 filas no traen link de imagen**; por eso la
foto ahora sale de la tienda y no del Excel.

**Estado de producción (no romper):** `bonosapp.com.ar` en vivo · mail live por Resend
(`info@bonosapp.com.ar`; el TXT DKIM `resend._domainkey` no se toca) · Contabilium y TiendaNube live
contra la tienda real — **todavía no se emitieron bonos** · padrón de 3 usuarios · el maestro lo
importa el cliente y aún no lo hizo.

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
