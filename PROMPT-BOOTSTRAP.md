# Prompt de bootstrap para Claude Code — NUTRIAPP

> **Para Fran (o quien sea el segundo dev):** copiá y pegá el bloque de abajo **tal cual** en tu Claude Code,
> la primera vez que abras el proyecto. Una sola vez es suficiente. Después de eso, cada sesión nueva arrancá
> simplemente pidiéndole a Claude que "lea el diario y el estado".
>
> **Para Santi:** el mismo prompt te sirve si se resetea el contexto, cambiás de máquina, o querés forzar un re-sync.

---

## Prompt (copy-paste entero)

```
Arrancás sesión en el proyecto NUTRIAPP. Antes de que te pida nada:

1. Leé `CLAUDE.md` (raíz del repo) completo. Es la guía maestra: stack, split de propiedad entre dos devs,
   regla de oro (nada mockeado / integraciones stub→live), convenciones, fases, comandos.

2. Leé `instrucciones_claude/00-setup-claude.md` para entender cómo coexisten dos Claudes
   (el mío y el del otro dev) sobre este mismo repo.

3. Leé las últimas 10 entradas de `instrucciones_claude/DIARIO.md` (bitácora append-only, las más nuevas
   están arriba). Quiero contexto real de qué se hizo, por qué, y qué problemas aparecieron — no re-descubras
   cosas ya resueltas.

4. Leé `instrucciones_claude/ESTADO.md` completo. Es el snapshot del presente: en qué está cada uno.

5. Confirmame el split de propiedad en una oración:
   - ¿Quién es dueño de `backend/`, infra, docker, keycloak, nginx, raíz?
   - ¿Quién es dueño de `frontend/`?
   - ¿Qué hacés si te pido tocar un archivo de un área que no es la mía?

6. Devolveme un resumen en 5 bullets:
   - Fase actual del proyecto.
   - En qué está Santi.
   - En qué está Fran.
   - Últimos 2 problemas técnicos que aparecieron (del DIARIO).
   - Próxima tarea lógica según el estado.

7. Finalmente: recordá estas reglas para toda la sesión:
   - No tocar archivos fuera de mi área de propiedad sin que yo te lo pida explícito.
   - NADA mockeado: datos por seed en DB, el front pega SIEMPRE al backend real, integraciones externas
     por adapter stub→live (ver CLAUDE.md "Regla de oro").
   - Al cerrar cualquier tarea no trivial, agregar una entrada al `DIARIO.md` siguiendo el formato del
     header de ese archivo.
   - Al cambiar de tarea, actualizar mi sección (y solo la mía) en `ESTADO.md`.
   - Seguir las convenciones de `CLAUDE.md` al leer código ajeno (no proponer reescribirlo).
   - Respuestas concisas: si algo se puede decir en 2 líneas, no lo digas en 10.

Cuando termines los 7 pasos, decime "listo, sincronizado" y esperá instrucción.
```

---

## Uso recurrente (sesiones siguientes, no bootstrap)

Para sesiones posteriores no hace falta el prompt completo. Alcanza con:

```
Arrancamos. Leé las últimas 10 entradas del DIARIO.md y mi sección de ESTADO.md,
después me das un resumen corto y esperás instrucción.
```
