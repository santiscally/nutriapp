# 03 — Integraciones con APIs externas

> Investigación hecha el 2026-07-17 sobre documentación oficial. **Regla de oro** (ver CLAUDE.md): todo se
> implementa completo contra la API real pero arranca en modo `stub` — la conexión real es Fase 2.
>
> ⚠️ **Credenciales**: Gon pasó email + API key de Contabilium y accesos de TiendaNube por WhatsApp.
> Van SOLO en `.env` (nunca en código, docs ni commits). Este doc no contiene credenciales.

## Patrón común stub→live

```yaml
# application.yml
nutriapp:
  integrations:
    contabilium: { mode: ${CONTABILIUM_MODE:stub}, client-id: ${CONTABILIUM_CLIENT_ID:}, client-secret: ${CONTABILIUM_CLIENT_SECRET:} }
    tiendanube:  { mode: ${TIENDANUBE_MODE:stub}, store-id: ${TIENDANUBE_STORE_ID:}, access-token: ${TIENDANUBE_ACCESS_TOKEN:}, client-secret: ${TIENDANUBE_CLIENT_SECRET:} }
    mail:        { mode: ${MAIL_MODE:stub}, ... }
```

- Interfaz (port) + `Http<X>Client` (real) + `Stub<X>Client` (lanza `IntegrationUnavailableException` con
  mensaje "integración <x> no conectada"). Bean elegido por `mode`.
- La lógica de negocio degrada: cupón queda `cupon_sync_estado=PENDIENTE`, notificación queda `QUEUED`,
  sync de productos no pisa el seed. Jobs de reconciliación reintentan lo pendiente — al flipear a `live`
  todo lo acumulado sale solo, sin tocar código.
- `GET /api/v1/admin/integraciones/estado` muestra modo + pendientes + última sync por proveedor.

---

## 1. Contabilium (ERP) — solo lectura de productos

Docu oficial: colección Postman https://documenter.getpostman.com/view/17702437/2s93shz9yz
Soporte API: api@contabilium.com. **No hay sandbox** — para probar sin tocar datos del cliente: cuenta trial propia.

### Auth
- OAuth2 `client_credentials`: **Email → `client_id`**, **API Key → `client_secret`** (así lo define la docu).
- `POST https://rest.contabilium.com/token` (form-urlencoded: `grant_type=client_credentials&client_id=...&client_secret=...`)
  → `{access_token, token_type: bearer, expires_in: 86399}` (~24 h).
- Token manager: cachear, renovar proactivo (~23 h) o ante 401, renovación serializada.
- Regenerar la API key en la web invalida la anterior de inmediato (coordinar con Gon si se rota).

### Endpoints que usamos
| Método | Path | Uso |
|---|---|---|
| GET | `/api/conceptos/search?filtro=&page=` | listado/búsqueda paginada (envelope `{Items, TotalPage, TotalItems}`, **50/página fijo**) |
| GET | `/api/conceptos/getByCodigo?codigo={SKU}` | lookup puntual por SKU (clave de conciliación) |
| GET | `/api/conceptos/rubros?includeChilds=true` | categorías (cachear) |
| GET | `/api/inventarios/getStockBySKU?codigo={SKU}` | stock on-demand (no cachear agresivo) |
| GET | `/api/usuarios/obtenerinfo` | health-check / validación de credenciales |

Shape de producto ("concepto"): `{Id, Tipo(Producto|Servicio|Combo), Nombre, Codigo, CodigoBarras, Descripcion,
Estado(Activo|Inactivo), Precio (neto), PrecioFinal (c/IVA), Iva, Stock, IdRubro, IdSubrubro, Foto (path relativo), IDMoneda}`.
`Nombre`/`Codigo` vienen en MAYÚSCULAS — normalizar al conciliar.

### ⚠️ Rate limit (crítico)
- **Argentina: 25 req/10 s.** Exceso → 429 + **bloqueo por IP de ~1 minuto** que afecta TODO lo que salga
  de esa IP — **incluida la facturación del propio cliente** si comparte red. Throttling propio a 15–20 req/10s,
  backoff ≥10 s ante 429, nunca reintento inmediato.
- No hay deltas en AR (`/api/stock/Novedades` es solo CL/UY) → sync de catálogo full-scan **nightly**, no continuo.
- Puntos a validar en la primera llamada real: params `filtro`/`page` de `conceptos/search` (no 100% explícitos
  en la docu) y la base URL del CDN de `Foto`.

### Rol en nuestro flujo
Contabilium es el ERP maestro pero **la compra pasa por TiendaNube** — el cupón y la orden viven allá.
Uso concreto: conciliación del catálogo por SKU (validar que lo recetable exista en el ERP) + posible
enriquecimiento de datos. El sync primario de productos es TiendaNube (Fase 2); Contabilium es secundario.
Si el cliente después configura una "integración Ecommerce" en Contabilium, hay callback push por SKU —
mejora futura, no MVP.

---

## 2. TiendaNube — productos, cupones, órdenes, webhooks

Docu oficial: https://tiendanube.github.io/api-documentation/ (API `2025-03`).
Base URL: `https://api.tiendanube.com/2025-03/{store_id}`.

### Auth
- **OAuth2 authorization code — hay que crear una app en el Partner Portal** (`partners.tiendanube.com`),
  aunque la tienda sea del cliente. Del portal salen `client_id` + `client_secret`; se puede crear una
  **tienda demo** para desarrollo (usarla en Fase 2 antes de tocar la tienda real TBC).
- Flujo: autorización del merchant → `code` (vence en 5 min) → `POST https://www.tiendanube.com/apps/authorize/token`
  → `{access_token, user_id}`. **`user_id` = store_id**. **El token NO expira** (solo si se re-emite o se
  desinstala la app): se persiste una vez.
- Headers: `Authorization: Bearer {token}` (v. 2025-03) + **`User-Agent: BonosApp (contacto.simpleapps@gmail.com)` obligatorio**
  (sin él: 400) + `Content-Type: application/json`.
- Scopes mínimos: `read_products, read_orders, write_coupons, read_coupons` (+ `write_products` si algún día escribimos).

### Cupones (el corazón de la trazabilidad)
```
POST /{store_id}/coupons
{
  "code": "RX-7K2M4X",              ← lo generamos NOSOTROS (= recetas.codigo)
  "type": "percentage",
  "value": "30.00",                  ← string decimal
  "max_uses": 1,                     ← un solo uso
  "start_date": "2026-07-17",
  "end_date": "2026-08-16",          ← = recetas.vence_at
  "products": [1234, 5678]           ← restringe a los productos de la receta (excluyente con categories)
}
→ 201 {id, valid, used, ...}         → guardar id en recetas.cupon_tiendanube_id
```
La API rechaza códigos duplicados (guard extra sobre nuestro UNIQUE local).

### Órdenes — detección de conversión
- El objeto order incluye **`coupon: [{id, code}]`** + `discount_coupon` (monto), `total`, `payment_status`,
  `paid_at`, `subtotal`, `customer`, `products`.
- `GET /orders/{id}` tras webhook; `GET /orders?payment_status=paid&updated_at_min=...` para el polling de
  respaldo (no se puede filtrar por cupón server-side — se filtra en memoria contra nuestros códigos).

### Webhooks
- `POST /{store_id}/webhooks` con `{"event": "order/paid", "url": "https://<dominio>/api/v1/webhooks/tiendanube"}`.
  URL **HTTPS obligatoria** (en dev no llegan: por eso existe el polling; para probar en vivo, túnel tipo ngrok/cloudflared).
- Payload mínimo `{store_id, event, id}` → hay que fetchear la orden después.
- Firma: header `x-linkedstore-hmac-sha256` = HMAC-SHA256 del body crudo con el **client_secret** de la app.
  Verificación constant-time, responder 200 rápido, procesar async (encolar). Los webhooks se REPITEN →
  idempotencia por `webhook_events (evento, recurso_id)`.
- Eventos que registramos: `order/paid` (primario), `order/created` (informativo, opcional).

### Rate limit
Leaky bucket 40 req / drenaje 2 req/s. Headers `x-rate-limit-*`; ante 429 backoff con `x-rate-limit-reset`.

### Puntos a validar contra la tienda demo (Fase 2)
- Shape exacto de `order.coupon[]` y formato de errores 422 (la docu no los ejemplifica).
- Si el % de descuento con `products: [...]` aplica solo a esos productos o al total (asumimos solo productos).

---

## 3. Email (canal de entrega de recetas)

- Proveedor **TBD — a cargo del cliente** (presupuesto §4). Recomendación según experiencia imedba:
  **AWS SES por SMTP** (sa-east-1, DKIM/SPF/DMARC sobre un subdominio del cliente) — barato y ya lo operamos.
  Alternativas: EnvíaloSimple (local), SendGrid (free tier ya no es permanente).
- Diseño: port `MailSender.send(MailRequest{to, subject, body, attachments})` + `SmtpMailSender` (JavaMailSender,
  STARTTLS 587) + `StubMailSender`. **Port directo de imedba** (`modules/notification/mail/`), incluida la
  lección: hostings que bloquean el 587 saliente → fallback adapter por API.
- Template de receta: HTML con logo inline por **CID** (no data-URI — Gmail los bloquea; lección imedba),
  datos de la receta, código de descuento grande, link a la tienda, vencimiento.

## 4. WhatsApp (canal de entrega de recetas) — **RESUELTO: link `wa.me`, no API**

**Decisión 2026-07-28, implementada el 2026-08-04 (tarea 2.4).** El envío por WhatsApp es **manual**:
el backend devuelve `waMeUrl` en el `RecetaResponse` y la nutricionista toca el botón, que le abre el
chat con la paciente con el mensaje ya escrito (código, descuento y vencimiento). Es la opción 3 de las
que estaban sobre la mesa, y quedó como definitiva:

- **No hace falta WABA**, ni número de empresa verificado, ni template aprobado por Meta, ni el costo por
  conversación. Nada de eso dependía de nosotros: dependía de trámites del cliente.
- El mensaje sale del número que la paciente ya conoce, que es como venían trabajando.
- El costo es que el envío **deja de ser garantizable por el sistema**: no hay acuse ni reintento. Por eso
  WhatsApp ya no es un canal de la cola de notificaciones ni una integración con estado — lo que no se
  envía solo, no se encola. El canal automático (con cola, reintentos y estado) es el **email**.

Implementación: `WaMeLinkBuilder` (`modules/receta/service/`) arma
`https://wa.me/<E164 sin +>?text=<mensaje url-encoded>`. Sólo devuelve link para recetas **PENDIENTE**
(una anulada o vencida daría un código muerto) y con teléfono utilizable. Se borraron
`integrations/whatsapp/**`, el valor `WHATSAPP` de `CanalNotificacion` (migración `V010`) y la config
`WHATSAPP_*`.

## 5. Resumen de modos por fase

| Integración | Fase 0–1 | Fase 2 (live) |
|---|---|---|
| Catálogo productos | seed Flyway (`origen=SEED`) | sync TiendaNube nightly + conciliación Contabilium por SKU |
| Cupón por receta | código local, `cupon_sync=PENDIENTE` | POST /coupons real + `CuponSyncJob` drena pendientes |
| Detección de compra | (nada llega; se puede simular insertando en `webhook_events`) | webhook `order/paid` + polling respaldo |
| Email | cola `QUEUED`, stub loguea | SMTP SES (o el proveedor que elija el cliente) |
| WhatsApp | — | link `wa.me` en el response, envío manual de la nutricionista (2.4). Sin cola ni proveedor |
