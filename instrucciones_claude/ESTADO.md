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

**Última actualización: 2026-09-24** — **no me queda ninguna tarea de desarrollo.** Todo en `main`.

**Hecho en esta tanda (post 1ª entrega):** S-01 a S-14, S-16, S-17 (lo aplica `scripts/deploy.sh`) y
S-18 (diagnóstico y registros listos) · adjuntos en `MailSender` para F-20 · el endpoint del PDF
documentado · `scripts/deploy.sh` · profesión/jurisdicción/matrícula obligatorias con interruptor ·
UI de S-10 en login y registro · **F-26 responsive, completa** (la tomé aunque es `frontend/`: menú de
celular, tablas como tarjetas, modales como hoja inferior; detalle en el PLAN). S-15 **descartado**.

**Lo que falta del proyecto, todo de Fran:**
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

**Última actualización: 2026-09-30.**

**Mi mitad está cerrada.** F-01 a F-13, F-15 a F-25 en `main`. **F-20 cerrada hoy**: el PDF usa la plantilla
del cliente y viaja adjunto al mail de emisión, verificado de punta a punta contra un SMTP local. F-26
(responsive) la tomó Santi.

**Lo único mío que queda abierto: F-14** — sacar "Descuento de bonos (%)" de la ficha del admin. Espera a que
**el cliente importe el maestro**: hasta entonces ese % es el único descuento que existe en el sistema y
sacarlo dejaría los bonos sin descuento.

**Dos cosas para preguntarle a Gon** (salieron al implementar la plantilla):
1. **"Firmado electrónicamente por"**: lo firma el **profesional que emitió el bono**. Si querían que dijera
   "BonosApp", es una línea.
2. **La plantilla tiene un typo**: *"No es necesario imprimirel cupón"*. Está en el arte, no se puede
   arreglar desde el código.

**Pendiente cosmético del PDF:** los valores van en Helvetica y la plantilla usa una tipografía redondeada
propia; de cerca se nota. Emparejarlo obliga a embeber la fuente en el PDF — se hace si el cliente lo pide.

**Pendiente de verificar cuando se despliegue:** que `/terminos` abra (en local la SPA se lo come; la config de
nginx dice que en prod no pasa) y dónde cae el mail en Gmail.

**Notas de entorno (mi máquina):**
- Mi `.env` de raíz tiene `MAIL_MODE=live`: **levantar el stack local con `MAIL_MODE=stub` por variable de
  entorno**, o el dispatcher manda mails reales por Resend. Para probar mail de verdad sin mandar nada afuera:
  `docker run -d --rm --name bonosapp-mailpit --network bonosapp_bonosapp-net -p 8025:8025 axllent/mailpit`
  y levantar con `MAIL_MODE=live MAIL_SMTP_HOST=bonosapp-mailpit MAIL_SMTP_PORT=1025 MAIL_SMTP_AUTH=false
  MAIL_SMTP_STARTTLS=false`.
- Backend sin Java en el host:
  `docker run --rm -v "<repo>/backend:/app" -v bonosapp-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn test`
  (Git Bash: `MSYS_NO_PATHCONV=1` y la ruta en formato Windows).
- `frontend/.env.local` tiene que apuntar al realm `bonosapp` (tenía el viejo `nutriapp`).
- La plantilla original quedó también en `frontend/src/assets/PLANTILLA_BONOSAPP.*`, sin usar: la que manda es
  la copia de `backend/src/main/resources/bono/`.
