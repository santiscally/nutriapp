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

**Última actualización: 2026-09-21** — arrancó la tanda de **modificaciones post 1ª entrega**. Confirmé
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

**🔴 Lo que falta para cerrar la tanda:** **S-13 y S-14** (endpoints admin PANEL/BONOS) están
contratados pero **no implementados** — Fran puede maquetar, no integrar. **S-03** (el filtro de
RUBRO que deja pasar cajas de cartón) necesita mirar datos de prod: hipótesis, `rubro_id` viene null
desde `/api/conceptos/search` y `permitido()` deja pasar lo ausente. Sin arrancar: S-05 a S-10,
S-15 a S-18.

**⚠️ Antes de desplegar esto a prod:** correr `V014`/`V015`, re-sincronizar el catálogo (cambiar
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

**Última actualización: 2026-09-19.**

**Hitos recientes cerrados:**
- ✅ **Mail EN VIVO en prod** (`bonosapp.com.ar`, ya sin pre-lanzamiento). Resend conectado, dominio verificado,
  `MAIL_MODE=live` en el VPS, `from = info@bonosapp.com.ar`. Verificado con un mail real entregado. (Cuidado:
  el TXT DKIM `resend._domainkey` se había borrado del DNS y rompía el envío — re-agregado, **no tocar**.)
- ✅ Contacto del front migrado a `info@bonosapp.com.ar` (`config.ts` + `frontend/.env.example`).
- ✅ Rebranding a BonosApp absorbido; rename "Receta → Bono Profesional" en el front.

**En qué estoy ahora:** arranca la tanda de **modificaciones post 1ª entrega** (feedback de Gon). El plan y la
división Fran/Santi está en `modificaciones post primera entrega/PLAN-modificaciones-post-entrega.md`.
**Mi mitad (Fran):** todo `frontend/` (renames a "Profesionales", validaciones de registro, desplegables
Provincias/Profesión, estados en masculino solo-display, filtro y % de descuento por producto, 2 solapas admin
nuevas PANEL/BONOS) + el **vertical mail** (link directo al producto en wa.me/mail, descripción del producto,
PDF del bono + re-descarga, deliverability desde el contenido).

**Bloqueado por el otro (contract-first, ver PLAN):** descuento por producto (S-02), campo Profesión (S-11),
comisión en `/me` (S-12), endpoints de las solapas admin (S-13/14), URL de tienda en el VPS (S-17), términos de
uso hosteados (S-16). **Bloqueo externo:** template del PDF del bono lo manda el cliente la próxima semana (F-20).
