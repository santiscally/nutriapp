// F.2 — Emitir Receta. Layout de dos columnas: izquierda = selección (paciente + productos),
// derecha = resumen sticky tipo carrito (items + descuento + total + emitir).
// El bono aplica a productos, no a cantidades: el cupón de TiendaNube restringe por producto y
// no sabe de unidades, así que una cantidad > 1 no se respetaría en la compra. Se recetan N
// productos distintos, uno de cada uno.

import { useMemo, useState } from "react";
import { ApiRequestError } from "../api/client";
import { emitirReceta } from "../api/recetas";
import { PacientePicker } from "../components/receta/PacientePicker";
import { ProductoBuscador } from "../components/receta/ProductoBuscador";
import { RecetaExito } from "../components/receta/RecetaExito";
import { Icon } from "../components/ui/Icon";
import { useAuth } from "../auth/AuthContext";
import { money, pctCorto } from "../lib/format";
import type { Paciente } from "../types/paciente";
import type { Producto } from "../types/producto";
import type { RecetaResponse } from "../types/receta";

interface ItemDraft {
  producto: Producto;
  indicaciones: string;
}

export function EmitirReceta() {
  const [paciente, setPaciente] = useState<Paciente | null>(null);
  const [items, setItems] = useState<ItemDraft[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RecetaResponse | null>(null);

  // F-16 / S-07 — TiendaNube crea los cupones combinables por defecto; el cliente quiere lo
  // contrario, así que el checkbox arranca destildado y se manda combinable=false.
  const [combinable, setCombinable] = useState(false);

  const { me } = useAuth();

  // S-02 — el descuento pasó a ser del PRODUCTO; el de la profesional (que viene en /me desde
  // V011) queda de fallback para los productos que todavía no están en el maestro.
  const descuentosDeProducto = useMemo(
    () => [...new Set(items.map((i) => i.producto.descuentoPct).filter((d): d is number => d != null))],
    [items],
  );
  // Dos productos con % distinto no entran en un mismo cupón: el backend responde 409. Se avisa
  // acá para no gastar el viaje ni dejarla adivinando por qué falló.
  const descuentosEnConflicto = descuentosDeProducto.length > 1;
  const descuentoPct = descuentosDeProducto.length === 1
    ? descuentosDeProducto[0]
    : me?.descuentoPct ?? 0;

  const selectedIds = useMemo(() => new Set(items.map((i) => i.producto.id)), [items]);

  const subtotal = useMemo(
    () => items.reduce((acc, i) => acc + i.producto.precio, 0),
    [items],
  );
  const totalConDescuento = subtotal * (1 - descuentoPct / 100);

  function addProducto(p: Producto) {
    setItems((prev) =>
      prev.some((i) => i.producto.id === p.id)
        ? prev
        : [...prev, { producto: p, indicaciones: "" }],
    );
  }
  function updateItem(id: string, patch: Partial<ItemDraft>) {
    setItems((prev) => prev.map((i) => (i.producto.id === id ? { ...i, ...patch } : i)));
  }
  function removeItem(id: string) {
    setItems((prev) => prev.filter((i) => i.producto.id !== id));
  }

  const canSubmit =
    paciente !== null && items.length > 0 && !submitting && !descuentosEnConflicto;

  async function onSubmit() {
    if (!paciente) return;
    setSubmitting(true);
    setError(null);
    try {
      const receta = await emitirReceta({
        pacienteId: paciente.id,
        items: items.map((i) => ({
          productoId: i.producto.id,
          cantidad: 1,
          indicaciones: i.indicaciones.trim() || undefined,
        })),
        combinable,
      });
      setResult(receta);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "No se pudo emitir el bono.");
    } finally {
      setSubmitting(false);
    }
  }

  function reset() {
    setPaciente(null);
    setItems([]);
    setCombinable(false);
    setError(null);
    setResult(null);
  }

  if (result) return <RecetaExito receta={result} onNueva={reset} />;

  return (
    <section className="emitir">
      {/* Barra superior: título + paciente. El paciente ocupaba una tarjeta entera para mostrar
          un solo dato; acá va en la misma línea y libera todo ese alto para el buscador. */}
      <header className="emitir__head">
        <h1 className="emitir__titulo">Emitir bono</h1>
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
            <h2 className="resumen-card__title">Bono profesional</h2>
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
                    <span className="cart-item__sub">{money(it.producto.precio)}</span>
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
                <span className="muted">Descuento ({pctCorto(descuentoPct)}%)</span>
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

            {/* F-16 — destildado por default: el bono no se suma a las promos de la tienda. */}
            <label className="resumen__combinable">
              <input
                type="checkbox"
                checked={combinable}
                onChange={(e) => setCombinable(e.target.checked)}
              />
              <span>Permitir combinar con otras promociones de la tienda</span>
            </label>

            {descuentosEnConflicto && (
              <div className="alert alert--error">
                Los productos del bono tienen descuentos distintos (
                {descuentosDeProducto.map((d) => `${pctCorto(d)}%`).join(" y ")}). Un cupón lleva un
                solo porcentaje: dejá uno solo, o emití un bono por cada uno.
              </div>
            )}

            {error && <div className="alert alert--error">{error}</div>}

            <button
              className="btn btn--primary resumen-card__submit"
              disabled={!canSubmit}
              onClick={onSubmit}
            >
              <Icon name="send" size={17} />
              {submitting ? "Emitiendo…" : "Emitir bono"}
            </button>
          </div>
        </aside>
      </div>
    </section>
  );
}
