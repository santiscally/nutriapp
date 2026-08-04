// Picker de paciente para Emitir Receta: búsqueda rápida (debounced) → seleccionar de la lista.
//
// El desplegable se abre al enfocar el campo, no al entrar a la pantalla. Antes el input tenía
// `autoFocus` y la búsqueda inicial va sin texto (trae la lista completa), así que apenas se abría
// Emitir Receta aparecía un panel de resultados que nadie pidió, tapando media pantalla.
//
// Todo el feedback (buscando / sin resultados / error) vive dentro del panel flotante: si va suelto
// debajo del input, aparece y desaparece con cada tecla y empuja lo que tiene abajo.

import { useCallback, useState } from "react";
import { buscarPacientes } from "../../api/pacientes";
import { useDebounce } from "../../hooks/useDebounce";
import { useFetch } from "../../hooks/useFetch";
import type { Paciente } from "../../types/paciente";

export function PacientePicker({ onSelect }: { onSelect: (p: Paciente) => void }) {
  const [q, setQ] = useState("");
  const [abierto, setAbierto] = useState(false);
  const dq = useDebounce(q);

  const fetcher = useCallback((s: AbortSignal) => buscarPacientes(dq, s), [dq]);
  const { data, loading, error } = useFetch(fetcher, [dq]);

  return (
    <div
      className="picker"
      onFocus={() => setAbierto(true)}
      // Sólo cierra si el foco se fue del picker entero: si no, al tabular del input a una opción
      // el panel se cerraría antes de que se pueda elegir.
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setAbierto(false);
      }}
      onKeyDown={(e) => e.key === "Escape" && setAbierto(false)}
    >
      <input
        className="picker__input"
        placeholder="Buscar paciente por nombre, apellido o email…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        role="combobox"
        aria-expanded={abierto}
        aria-controls="picker-resultados"
      />

      {abierto && (
        <div className="picker__panel" id="picker-resultados">
          {loading && <p className="picker__msg muted">Buscando…</p>}
          {error && <div className="alert alert--error">{error}</div>}

          {data && data.content.length === 0 && !loading && (
            <p className="picker__msg muted">Sin resultados{q ? ` para “${q}”` : ""}.</p>
          )}

          {data && data.content.length > 0 && (
            <ul className="picker__results">
              {data.content.map((p) => (
                <li key={p.id}>
                  <button
                    type="button"
                    className="picker__option"
                    onClick={() => {
                      onSelect(p);
                      setAbierto(false);
                    }}
                  >
                    <span className="picker__name">
                      {p.nombre} {p.apellido}
                    </span>
                    <span className="muted">{p.email}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
