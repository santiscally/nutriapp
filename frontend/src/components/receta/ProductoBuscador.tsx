// Buscador de productos: texto libre (q) + dropdowns marca/laboratorio/presentación (de /productos/filtros).
// Cada resultado se agrega a la receta con "Agregar". Los ya agregados se marcan y no se duplican.

import { useCallback, useState } from "react";
import { buscarProductos, getFiltros } from "../../api/productos";
import { useDebounce } from "../../hooks/useDebounce";
import { useFetch } from "../../hooks/useFetch";
import { money } from "../../lib/format";
import type { Producto } from "../../types/producto";

interface Props {
  onAdd: (p: Producto) => void;
  selectedIds: Set<string>;
}

export function ProductoBuscador({ onAdd, selectedIds }: Props) {
  const [q, setQ] = useState("");
  const [marca, setMarca] = useState("");
  const [laboratorio, setLaboratorio] = useState("");
  const [presentacion, setPresentacion] = useState("");
  const dq = useDebounce(q);

  const filtros = useFetch(useCallback((s: AbortSignal) => getFiltros(s), []));

  const productos = useFetch(
    useCallback(
      (s: AbortSignal) => buscarProductos({ q: dq, marca, laboratorio, presentacion }, s),
      [dq, marca, laboratorio, presentacion],
    ),
    [dq, marca, laboratorio, presentacion],
  );

  const f = filtros.data;

  return (
    <div>
      <div className="buscador__controls">
        <input
          className="picker__input"
          placeholder="Buscar producto por nombre, SKU o descripción…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <div className="buscador__filtros">
          <select value={marca} onChange={(e) => setMarca(e.target.value)}>
            <option value="">Marca (todas)</option>
            {f?.marcas.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
          <select value={laboratorio} onChange={(e) => setLaboratorio(e.target.value)}>
            <option value="">Laboratorio (todos)</option>
            {f?.laboratorios.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
          <select value={presentacion} onChange={(e) => setPresentacion(e.target.value)}>
            <option value="">Presentación (todas)</option>
            {f?.presentaciones.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </div>
      </div>

      {productos.loading && <p className="muted">Buscando productos…</p>}
      {productos.error && <div className="alert alert--error">{productos.error}</div>}
      {productos.data && productos.data.content.length === 0 && (
        <p className="muted">Sin productos para esos filtros.</p>
      )}

      {productos.data && productos.data.content.length > 0 && (
        <ul className="prod-list">
          {productos.data.content.map((p) => {
            const added = selectedIds.has(p.id);
            return (
              <li key={p.id} className="prod-row">
                <div className="prod-row__info">
                  <span className="prod-row__name">{p.nombre}</span>
                  <span className="muted">
                    {p.marca} · {p.presentacion} · <span className="mono">{p.sku}</span>
                  </span>
                </div>
                <span className="prod-row__price">{money(p.precio)}</span>
                <button
                  className="btn btn--sm btn--primary"
                  disabled={added}
                  onClick={() => onAdd(p)}
                >
                  {added ? "Agregado" : "Agregar"}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
