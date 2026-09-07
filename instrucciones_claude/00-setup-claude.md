# Setup de Claude Code — Proyecto BONOSAPP

> Este doc es para que **cualquiera** de los dos devs arranque Claude Code con el mismo criterio que el otro.
> Está pensado para correrse **una sola vez** por máquina/clon del repo, y después la convivencia entre ambos
> Claudes queda resuelta por `CLAUDE.md`, `DIARIO.md` y `ESTADO.md`.

---

## 1. Requisitos previos

- Claude Code instalado (`https://claude.ai/code` o la CLI).
- Repo clonado y `cd bonosapp`.
- Docker Desktop corriendo (para poder levantar el stack).
- Node 20.19+ y Java 21 si vas a correr fuera de Docker (opcional).

## 2. Abrir Claude Code en la raíz del repo

**Importante:** abrir Claude siempre desde la raíz `bonosapp/`, no desde un subdirectorio. De esa manera
`CLAUDE.md` se carga automáticamente como contexto y Claude entiende el split de propiedad y la regla de oro
(nada mockeado, integraciones stub→live).

## 3. Ejecutar el prompt de bootstrap (solo la primera vez)

Copiá y pegá **tal cual** el prompt que está en `PROMPT-BOOTSTRAP.md` (raíz del repo) en tu Claude. Ese prompt
hace que tu Claude:

1. Lea `CLAUDE.md` completo.
2. Lea este doc.
3. Lea las últimas ~10 entradas del `DIARIO.md`.
4. Lea `ESTADO.md`.
5. Confirme que entendió el split de propiedad (**Santi = backend/infra**, **Fran = frontend**).
6. Te devuelva un resumen de 5 bullets del estado actual.

A partir de ese momento, en cada sesión nueva Claude lee `CLAUDE.md` automáticamente (porque está en la raíz)
y, si le decís "arrancás sesión, leé el diario y el estado", queda sincronizado en 1 llamada.

## 4. Convención de uso durante el trabajo

- **Al arrancar cada sesión** (no solo la primera): pedile a Claude que lea las últimas entradas del `DIARIO.md`
  y tu sección de `ESTADO.md`.
- **Al cerrar una tarea no trivial**: pedile que agregue una entrada al `DIARIO.md` (formato en el header del archivo).
- **Al cambiar de tarea**: pedile que actualice tu sección de `ESTADO.md` (sobreescribe, no appendea).
- **Nunca** le pidas que toque la sección del otro en `ESTADO.md`. El `CLAUDE.md` ya tiene esa regla grabada,
  pero recordásela si la viola.
- **Contrato API**: la fuente de verdad es Swagger + `05-api-endpoints.md`. Si el back cambia un DTO, va entrada
  en el DIARIO con "Impacto para el otro" — Fran actualiza su `type` espejo cuando la lea.

## 5. Split de propiedad (lo repito porque es crítico)

| Área | Dueño | Claude del otro puede... |
|---|---|---|
| `backend/`, `docker-compose*.yml`, `keycloak/`, `nginx/`, `db/`, `scripts/`, `.env.example`, raíz | **Santi** | leer; tocar solo si Santi lo pide explícito |
| `frontend/` | **Fran** | leer; tocar solo si Fran lo pide explícito |
| `instrucciones_claude/DIARIO.md` | compartido (append-only) | ambos agregan entradas, nadie edita las viejas |
| `instrucciones_claude/ESTADO.md` | compartido (cada uno su sección) | solo toca la propia |
| `CLAUDE.md`, otros `instrucciones_claude/*.md` | **Santi** | cambios los hace Santi; Fran puede proponerlos |

Si a tu Claude le pedís algo que cae en un área que no te pertenece, tiene que **parar y avisar** antes de
modificar nada.

## 6. Particularidad de este proyecto: ventana de Fran

Fran trabaja **hasta el martes 21 de julio inclusive** y vuelve **~12 de agosto** (3 semanas de vacaciones).
Durante ese hueco, Santi es el único activo: si necesita un cambio en `frontend/`, lo documenta en el DIARIO
como pendiente para Fran, y solo lo toca él mismo si es bloqueante (dejándolo explícito en el DIARIO con
entrada "toqué frontend por bloqueo, revisar a la vuelta").
