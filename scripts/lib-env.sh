#!/usr/bin/env bash
# Lee un .env sin ejecutarlo.
#
# `set -a; . .env` interpreta el archivo como script: un valor con paréntesis lo hace fallar
# entero ("syntax error near unexpected token") y uno con $(...) ejecutaría el comando. El .env
# real tiene las dos cosas, porque ahí viven user-agents y secretos que nadie escribió pensando
# en la sintaxis de bash.
#
# Lo que ya venga del entorno gana sobre el archivo.

cargar_env() {
  local archivo="$1" linea clave valor
  [ -f "${archivo}" ] || return 0
  while IFS= read -r linea || [ -n "${linea}" ]; do
    linea="${linea%$'\r'}"
    case "${linea}" in ''|'#'*) continue ;; esac
    case "${linea}" in *=*) ;; *) continue ;; esac
    clave="${linea%%=*}"
    valor="${linea#*=}"
    clave="${clave#"${clave%%[![:space:]]*}"}"
    clave="${clave#export }"
    case "${clave}" in ''|*[!A-Za-z0-9_]*) continue ;; esac
    if [ -n "${!clave-}" ]; then
      continue
    fi
    if [ "${valor#\"}" != "${valor}" ] && [ "${valor%\"}" != "${valor}" ]; then
      valor="${valor#\"}"; valor="${valor%\"}"
    elif [ "${valor#\'}" != "${valor}" ] && [ "${valor%\'}" != "${valor}" ]; then
      valor="${valor#\'}"; valor="${valor%\'}"
    fi
    export "${clave}=${valor}"
  done < "${archivo}"
}
