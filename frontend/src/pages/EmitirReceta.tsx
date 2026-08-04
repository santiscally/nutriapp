// F.2 — Emitir Receta. Layout de dos columnas: izquierda = selección (paciente + productos),
// derecha = resumen sticky tipo carrito (items + descuento + total + emitir).
// El modelo soporta N items; la UI arranca en 1 pero permite agregar/quitar varios.

import { useMemo, useState } from "react";
import { ApiRequestError } from "../api/client";
import { emitirReceta } from "../api/recetas";
import { PacientePicker } from "../components/receta/PacientePicker";
import { ProductoBuscador } from "../components/receta/ProductoBuscador";
import { RecetaExito } from "../components/receta/RecetaExito";
import { Icon } from "../components/ui/Icon";
import { useAuth } from "../auth/AuthContext";
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

  // El descuento es el propio de esta nutricionista y lo define el admin; ella no lo edita.
  // Viene en /me desde V011, cuando se eliminó el valor global (y con él GET /configuracion).
  const { me } = useAuth();
  const descuentoPct = me?.descuentoPct ?? 0;

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
      {/* Barra superior: título + paciente. El paciente ocupaba una tarjeta entera para mostrar
          un solo dato; acá va en la misma línea y libera todo ese alto para el buscador. */}
      <header className="emitir__head">
        <h1 className="emitir__titulo">Emitir receta</h1>
        <div className="emitir__paciente">
          {paciente ? (
            <>
              <span className="emitir__paciente-info">
                <Icon name="users" size={15} />
                <strong>
                  {paciente.nombre} {paciente.apellido}
                </strong>
                <span className="muted">{paciente.email}</span>
              </span>
              <button className="btn btn--sm btn--ghost" onClick={() => setPaciente(null)}>
                Cambiar
              </button>
            </>
          ) : (
            <PacientePicker onSelect={setPaciente} />
          )}
        </div>
      </header>

      {/* Dos paneles de alto fijo. La página no scrollea: scrollean por dentro la lista de
          resultados y la de items, que es lo único que puede crecer sin límite. Así el buscador y
          el botón de emitir están siempre a la vista, sin perseguirlos con la rueda. */}
      <div className="emitir__grid">
        <div className="card emitir__panel">
          <ProductoBuscador onAdd={addProducto} selectedIds={selectedIds} />
        </div>

        <aside className="card emitir__panel emitir__resumen">
          <div className="emitir__resumen-head">
            <h2 className="resumen-card__title">Receta</h2>
            {items.length > 0 && (
              <span className="muted">
                {items.length} producto{items.length === 1 ? "" : "s"}
              </span>
            )}
          </div>

          {items.length === 0 ? (
            <div className="resumen-card__empty">
              <Icon name="pill" size={28} />
              <p className="muted">
                {paciente
                  ? "Agregá productos del buscador."
                  : "Elegí un paciente y agregá productos."}
              </p>
            </div>
          ) : (
            <ul className="cart emitir__items">
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

          {/* Pie fijo del panel: totales y emitir siempre visibles, aunque el carrito scrollee. */}
          <div className="emitir__pie">
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
              Valores aproximados: el precio final lo define la tienda al comprar.
            </p>

            {error && <div className="alert alert--error">{error}</div>}

            <button
              className="btn btn--primary resumen-card__submit"
              disabled={!canSubmit}
              onClick={onSubmit}
            >
              <Icon name="send" size={17} />
              {submitting ? "Emitiendo…" : "Emitir receta"}
            </button>
          </div>
        </aside>
      </div>
    </section>
  );
}
