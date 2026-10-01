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

**Última actualización: 2026-10-01.** **F-14 hecha** (la tomé aunque toca `frontend/`, por pedido del
usuario): el % de descuento sale de la profesional en todo el sistema y queda sólo la comisión. Sin
deployar todavía.

**De la tanda post 1ª entrega no queda desarrollo abierto.** S-01 a S-14, S-16, S-17, S-18, F-26 y F-14
hechos; S-15 descartado; la mitad de Fran, cerrada (ver su sección). El usuario da por solucionado lo de
Promociones/Spam.

**Pendiente de operación:** que el cliente suba el maestro (lo hace hoy) y deployar F-14 con
`scripts/deploy.sh`; después, "Sincronizar productos" y "Mapear productos" en Integraciones. Sigue sin
decidir si se reescriben los 5 commits con atribución a Claude (`c236fa1`, `dde2bf6`, `d051370`,
`0c84e7e`, `ff2e780`).

**Estado de producción (no romper):** `bonosapp.com.ar` en vivo · mail live por Resend (el TXT DKIM
`resend._domainkey` no se toca) · Contabilium y TiendaNube live contra la tienda real — **se están emitiendo
y usando bonos** · DMARC con `rua` publicado el 30/09.

## Fran / frontend

**Última actualización: 2026-10-01.**

**No me queda desarrollo abierto.** F-01 a F-13 y F-15 a F-25, en `main` y **desplegadas**. F-14 la tomó
Santi (está en `main`, **sin deployar**); F-26 (responsive) también fue suya.

**Lo último que se cerró — F-20, el bono es el mail.** El cuerpo del mail replica la plantilla del cliente
en HTML (logo, banda verde, código, pie con TBC y QR) y **no lleva adjuntos**. El PDF sigue vivo para el
botón de descarga de la app. Toqué `integrations/mail/` (zona de Santi) con OK del usuario: el port suma el
cuerpo HTML y `SmtpMailSender` arma multipart/alternative — el texto plano viaja siempre.

**Verificado en prod:** `/`, `/terminos`, `/mail/bonosapp-logo.png`, `/mail/tbc-qr.png` y
`/api/v1/profesiones` responden 200; el mail llega con el template y las imágenes cargan.

**⚠️ Lo único del circuito que nadie probó todavía:** que un **cupón real se aplique en el checkout**.
Siempre se probó con TiendaNube en `stub`, donde el cupón no se crea. El test: emitir un bono en prod, abrir
el link, sumar el producto al carrito y mirar el total.

**Mail y Promociones — cerrado del lado técnico.** El mail llega a la bandeja (DKIM/SPF/DMARC alinean), pero
cae en **Promociones**. La pestaña la decide el clasificador de Gmail por contenido y comportamiento, no la
autenticación. Las palancas que quedan (sacar el botón, menos imágenes, cuerpo más sobrio) chocan con el
diseño que pidió el cliente: es decisión suya, no un bug.

**Para Santi, dos correcciones anotadas en el DIARIO (su zona, no las toqué):**
- `scripts/deploy.sh` aborta en 9/9 por un falso negativo: el smoke pide `/terminos` a `localhost` y cae en
  el catch-all `return 444` de nginx. Se arregla pasándole `--header="Host: bonosapp.com.ar"`.
- `DEPLOY.md` dice `/root/bonosapp`; el checkout real es `/root/nutriapp`.

**Con el cliente:** la plantilla corregida (el typo "imprimirel cupón" **ya no está en el mail** —ese texto
lo escribimos nosotros— pero **sigue en el PDF** de descarga) y confirmar que "Firmado electrónicamente por"
lleve el nombre del profesional y no "BonosApp".

**Notas de entorno (mi máquina):**
- Mi `.env` de raíz tiene `MAIL_MODE=live`: **levantar el stack local con `MAIL_MODE=stub`**, o el dispatcher
  manda mails reales por Resend. Para probar mail sin mandar nada afuera:
  `docker run -d --rm --name bonosapp-mailpit --network bonosapp_bonosapp-net -p 8025:8025 axllent/mailpit`
  + `MAIL_MODE=live MAIL_SMTP_HOST=bonosapp-mailpit MAIL_SMTP_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false`.
- Las imágenes del mail salen de `frontend/public/mail/` y se publican **con el frontend**: desplegar sólo el
  backend las deja rotas.
- Backend sin Java en el host:
  `docker run --rm -v "<repo>/backend:/app" -v bonosapp-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn test`
  (Git Bash: `MSYS_NO_PATHCONV=1` y la ruta en formato Windows).
