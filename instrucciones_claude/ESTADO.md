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

**Última actualización: 2026-09-24** — **en curso: F-26, responsive** (la tomamos nosotros, aunque es
`frontend/`). Fuera de eso no me queda ninguna tarea de desarrollo. Todo en `main`.

**Hecho en esta tanda (post 1ª entrega):** S-01 a S-14, S-16, S-17 (lo aplica `scripts/deploy.sh`) y
S-18 (diagnóstico y registros listos) · adjuntos en `MailSender` para F-20 · el endpoint del PDF
documentado · `scripts/deploy.sh` · profesión/jurisdicción/matrícula obligatorias con interruptor ·
UI de S-10 en login y registro · primera pasada de responsive (el *layout* de las pantallas; el resto
quedó como **F-26**, de Fran). S-15 **descartado**.

**Lo que falta del proyecto:** F-26 (mía, en curso) y, de Fran:
- **F-20** — conectar el PDF al mail del paciente. Ya no está bloqueada: hay template provisorio y
  `MailSender` adjunta.
- **F-14** — sacar el % de descuento de la ficha del admin. Espera a que el cliente importe el maestro:
  hasta entonces es el único descuento que existe.

**No son tareas de desarrollo** (acciones de operación, para cuando se decida): correr
`scripts/deploy.sh` en el VPS, publicar el DMARC en Hostinger (valores en `DEPLOY.md`), y decidir si se
reescriben los 5 commits con atribución a Claude (`c236fa1`, `dde2bf6`, `d051370`, `0c84e7e`, `ff2e780`).

**Estado de producción (no romper):** `bonosapp.com.ar` en vivo · mail live por Resend (el TXT DKIM
`resend._domainkey` no se toca) · Contabilium y TiendaNube live contra la tienda real — **se están emitiendo
y usando bonos** · padrón de 3 usuarios · el maestro todavía no lo importó el cliente.

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
