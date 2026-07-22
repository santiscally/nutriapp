// Picker de paciente para Emitir Receta: búsqueda rápida (debounced) → seleccionar de la lista.

import { useCallback, useState } from "react";
import { buscarPacientes } from "../../api/pacientes";
import { useDebounce } from "../../hooks/useDebounce";
import { useFetch } from "../../hooks/useFetch";
import type { Paciente } from "../../types/paciente";

export function PacientePicker({ onSelect }: { onSelect: (p: Paciente) => void }) {
  const [q, setQ] = useState("");
  const dq = useDebounce(q);

  const fetcher = useCallback((s: AbortSignal) => buscarPacientes(dq, s), [dq]);
  const { data, loading, error } = useFetch(fetcher, [dq]);

  return (
    <div className="picker">
      <input
        className="picker__input"
        placeholder="Buscar paciente por nombre, apellido o email…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        autoFocus
      />

      {loading && <p className="muted">Buscando…</p>}
      {error && <div className="alert alert--error">{error}</div>}

      {data && data.content.length === 0 && (
        <p className="muted">Sin resultados{q ? ` para “${q}”` : ""}.</p>
      )}

      {data && data.content.length > 0 && (
        <ul className="picker__results">
          {data.content.map((p) => (
            <li key={p.id}>
              <button className="picker__option" onClick={() => onSelect(p)}>
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
  );
}
