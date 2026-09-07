# BONOSAPP — Bonos profesionales para nutricionistas

> Ex **NutriApp**. Rebranding pedido por el cliente el 2026-09-03, antes del lanzamiento: dominio
> `bonosapp.com.ar`, isotipo sin cambios, wordmark nuevo. El kit oficial está en `brand/`.

Webapp donde nutricionistas validados emiten bonos profesionales con descuento sobre productos del
ecosistema del cliente (ERP Contabilium / tienda TiendaNube). El paciente recibe el bono por mail y
WhatsApp con un código de descuento único y compra en la tienda online; el bono queda trazado
(pendiente → aplicado / vencido a los 30 días) y el nutricionista ve su cierre mensual de comisiones.

Cliente: Gon (jeianell / tienda TBC). Presupuesto: `presupuesto_nutriapp.pdf`. Plazo: 2 meses.

## Stack

| Capa | Tecnología |
|------|-----------|
| Backend | Java 21 + Spring Boot 3.3.x (Santi) |
| Frontend | React 19 / TypeScript / Vite (Fran) |
| Base de datos | PostgreSQL 16 |
| Autenticación | Keycloak 25 (OIDC/JWT) |
| Infra | Docker + Docker Compose (+ nginx TLS en prod) |
| Integraciones | Contabilium · TiendaNube · email · WhatsApp — todas con modo `stub|live` |

## Estructura del repo (objetivo)

```
nutriapp/                   # el repo conserva el nombre viejo (ver nota de rebranding)
├── backend/                # Spring Boot 3 + Java 21 (Santi)
├── brand/                  # Kit de marca BonosApp que mandó el cliente (png + jpg)
├── frontend/               # React 19 + TS + Vite (Fran)
├── keycloak/               # Realm export + bootstrap
├── db/init/                # Init SQL/SH de Postgres (crea DB keycloak)
├── nginx/                  # Reverse proxy TLS (prod)
├── docker-compose.yml      # Stack dev completo
├── docker-compose.prod.yml # Override prod
├── .env.example            # Plantilla de variables de entorno
├── CLAUDE.md               # Guía maestra para Claude Code (leer PRIMERO)
├── PROMPT-BOOTSTRAP.md     # Prompt de sincronización para el segundo dev
└── instrucciones_claude/   # Docs de diseño + coordinación (arquitectura, ERD, integraciones, fases, contrato REST, DIARIO, ESTADO)
```

> **Estado actual:** Fase 0 — docs de diseño completas; scaffolding de código en curso.
> Plan y asignación de tareas: `instrucciones_claude/04-plan-de-fases.md`.

## Puesta en marcha (dev)

```bash
cp .env.example .env    # editar credenciales — NUNCA commitear .env
docker compose up -d --build
```

| Servicio | URL local |
|----------|-----------|
| Frontend (SPA) | http://localhost:5173 |
| Backend + Swagger | http://localhost:8080 · /swagger-ui.html |
| Keycloak | http://localhost:8081 |
| PostgreSQL | localhost:5432 (DBs: `nutriapp`, `keycloak`) |

## Reglas de la casa

1. **Nada mockeado**: datos por seed en DB; el front pega siempre al backend real.
2. **Integraciones stub→live**: código completo contra la API real, activación por `.env`. Detalle en
   `instrucciones_claude/03-integraciones-apis.md`.
3. **Dos devs, dos Claudes**: split de propiedad y coordinación por `DIARIO.md`/`ESTADO.md` — ver
   `CLAUDE.md` y `instrucciones_claude/00-setup-claude.md`.
4. **Secretos solo en `.env`** (las credenciales que pasó el cliente jamás van al repo).

## Licencia

Privado — Simple Apps / cliente.
