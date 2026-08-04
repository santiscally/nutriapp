// F.2 — Emitir Receta. Layout de dos columnas: izquierda = selección (paciente + productos),
// derecha = resumen sticky tipo carrito (items + descuento + total + emitir).
// El modelo soporta N items; la UI arranca en 1 pero permite agregar/quitar varios.

import { useCallback, useMemo, useState } from "react";
import { ApiRequestError } from "../api/client";
import { getConfiguracion } from "../api/configuracion";
import { emitirReceta } from "../api/recetas";
import { PacientePicker } from "../components/receta/PacientePicker";
import { ProductoBuscador } from "../components/receta/ProductoBuscador";
import { RecetaExito } from "../components/receta/RecetaExito";
import { Icon } from "../components/ui/Icon";
import { useFetch } from "../hooks/useFetch";
import { money } from "../lib/format";
import type { Paciente } from "../types/paciente";
import type { Producto } from "../types/producto";
import type { RecetaResponse } from "../types/receta";

interface ItemDraft {
  producto: Producto;
  cantidad: number;
  indicaciones: string;
}

export function EmitirReceta() {
  const [paciente, setPaciente] = useState<Paciente | null>(null);
  const [items, setItems] = useState<ItemDraft[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RecetaResponse | null>(null);

  // Descuento fijo global: lo define el admin, el nutricionista no lo edita.
  const configFetcher = useCallback((s: AbortSignal) => getConfiguracion(s), []);
  const { data: config } = useFetch(configFetcher);
  const descuentoPct = config?.descuentoPct ?? 0;

  const selectedIds = useMemo(() => new Set(items.map((i) => i.producto.id)), [items]);

  const subtotal = useMemo(
    () => items.reduce((acc, i) => acc + i.producto.precio * i.cantidad, 0),
    [items],
  );
  const totalConDescuento = subtotal * (1 - descuentoPct / 100);

  function addProducto(p: Producto) {
    setItems((prev) =>
      prev.some((i) => i.producto.id === p.id)
        ? prev
        : [...prev, { producto: p, cantidad: 1, indicaciones: "" }],
    );
  }
  function updateItem(id: string, patch: Partial<ItemDraft>) {
    setItems((prev) => prev.map((i) => (i.producto.id === id ? { ...i, ...patch } : i)));
  }
  function removeItem(id: string) {
    setItems((prev) => prev.filter((i) => i.producto.id !== id));
  }

  const canSubmit = paciente !== null && items.length > 0 && !submitting;

  async function onSubmit() {
    if (!paciente) return;
    setSubmitting(true);
    setError(null);
    try {
      const receta = await emitirReceta({
        pacienteId: paciente.id,
        items: items.map((i) => ({
          productoId: i.producto.id,
          cantidad: i.cantidad,
          indicaciones: i.indicaciones.trim() || undefined,
        })),
      });
      setResult(receta);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "No se pudo emitir la receta.");
    } finally {
      setSubmitting(false);
    }
  }

  function reset() {
    setPaciente(null);
    setItems([]);
    setError(null);
    setResult(null);
  }

  if (result) return <RecetaExito receta={result} onNueva={reset} />;

  return (
    <section className="emitir">
      <div style={{ marginBottom: "1.5rem" }}>
        <h1 className="page-title" style={{ marginBottom: "0.35rem" }}>
          Emitir receta
        </h1>
        <p className="muted">
          Elegí el paciente y los productos; el código de descuento se genera al emitir.
        </p>
      </div>

      <div className="emitir__grid">
        {/* ---- Columna izquierda: selección ---- */}
        <div className="emitir__main">
          <div className="card step">
            <h2 className="step__title">1 · Paciente</h2>
            {paciente ? (
              <div className="chip">
                <span>
                  <strong>
                    {paciente.nombre} {paciente.apellido}
                  </strong>{" "}
                  · {paciente.email}
                </span>
                <button className="btn btn--sm btn--ghost" onClick={() => setPaciente(null)}>
                  Cambiar
                </button>
              </div>
            ) : (
              <PacientePicker onSelect={setPaciente} />
            )}
          </div>

          <div className="card step">
            <h2 className="step__title">2 · Productos</h2>
            <ProductoBuscador onAdd={addProducto} selectedIds={selectedIds} />
          </div>
        </div>

        {/* ---- Columna derecha: resumen sticky ---- */}
        <aside className="emitir__summary">
          <div className="card resumen-card">
            <h2 className="resumen-card__title">Resumen de la receta</h2>
            <p className="resumen-card__para muted">
              {paciente ? (
                <>
                  Para <strong>{paciente.nombre} {paciente.apellido}</strong>
                </>
              ) : (
                "Elegí un paciente para empezar."
              )}
            </p>

            {items.length === 0 ? (
              <div className="resumen-card__empty">
                <Icon name="pill" size={28} />
                <p className="muted">Agregá productos para armar la receta.</p>
              </div>
            ) : (
              <ul className="cart">
                {items.map((it) => (
                  <li key={it.producto.id} className="cart-item">
                    <div className="cart-item__top">
                      <span className="cart-item__name">{it.producto.nombre}</span>
                      <button
                        className="cart-item__remove"
                        onClick={() => removeItem(it.producto.id)}
                        aria-label="Quitar"
                      >
                        <Icon name="x-circle" size={16} />
                      </button>
                    </div>
                    <div className="cart-item__row">
                      <label className="cart-item__qty">
                        <input
                          type="number"
                          min={1}
                          value={it.cantidad}
                          onChange={(e) =>
                            updateItem(it.producto.id, {
                              cantidad: Math.max(1, Number(e.target.value) || 1),
                            })
                          }
                        />
                        <span className="muted">× {money(it.producto.precio)}</span>
                      </label>
                      <span className="cart-item__sub">
                        {money(it.producto.precio * it.cantidad)}
                      </span>
                    </div>
                    <input
                      className="cart-item__ind"
                      placeholder="Indicaciones (ej. 1 medida post-entreno)"
                      value={it.indicaciones}
                      onChange={(e) => updateItem(it.producto.id, { indicaciones: e.target.value })}
                    />
                  </li>
                ))}
              </ul>
            )}

            <div className="resumen">
              <div>
                <span className="muted">Subtotal</span>
                <span>{money(subtotal)}</span>
              </div>
              <div>
                <span className="muted">Descuento ({descuentoPct}%)</span>
                <span>−{money(subtotal - totalConDescuento)}</span>
              </div>
              <div className="resumen__total">
                <span>Total estimado</span>
                <span>{money(totalConDescuento)}</span>
              </div>
            </div>

            {/* C-02 (call 24:35): la nutricionista necesita una referencia de precio para
                responderle al paciente, pero el valor final lo define la tienda — puede tener
                promos que se acumulan con este descuento. La receta que recibe el paciente y el
                historial NO llevan importes: sólo se ven acá. */}
            <p className="resumen__disclaimer">
              Valores aproximados. El precio final lo define la tienda al momento de la compra y
              puede cambiar sin previo aviso.
            </p>

            {error && <div className="alert alert--error">{error}</div>}

            <button
              className="btn btn--primary btn--lg resumen-card__submit"
              disabled={!canSubmit}
              onClick={onSubmit}
            >
              <Icon name="send" size={18} />
              {submitting ? "Emitiendo…" : "Emitir receta"}
            </button>
          </div>
        </aside>
      </div>
    </section>
  );
}
