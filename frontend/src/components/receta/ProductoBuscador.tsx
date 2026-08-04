// Buscador de productos del emisor de recetas.
//
// `q` va contra nombre, SKU, descripción, código de barras y los tags del maestro, y el backend
// devuelve el resultado rankeado (nombre → descripción → tag). Los filtros de departamento,
// categoría y subcategoría se encadenan con el árbol que manda /productos/filtros: sueltas, las
// subcategorías son 142 opciones en un dropdown y no las usa nadie.
//
// Los productos que no están en el maestro de TBC no tienen ni taxonomía ni imagen ni tags: la fila
// tiene que verse bien igual (solo 1 de cada 4 tiene imagen).

import { useCallback, useMemo, useState } from "react";
import { buscarProductos, getFiltros } from "../../api/productos";
import { useDebounce } from "../../hooks/useDebounce";
import { useFetch } from "../../hooks/useFetch";
import { money } from "../../lib/format";
import type { Producto } from "../../types/producto";
import { Modal } from "../ui/Modal";

interface Props {
  onAdd: (p: Producto) => void;
  selectedIds: Set<string>;
}

const PAGE_SIZE = 8;

export function ProductoBuscador({ onAdd, selectedIds }: Props) {
  const [q, setQ] = useState("");
  const [marca, setMarca] = useState("");
  const [departamento, setDepartamento] = useState("");
  const [categoria, setCategoria] = useState("");
  const [subcategoria, setSubcategoria] = useState("");
  const [laboratorio, setLaboratorio] = useState("");
  const [tag, setTag] = useState("");
  const [conStock, setConStock] = useState(false);
  const [precioMin, setPrecioMin] = useState("");
  const [precioMax, setPrecioMax] = useState("");
  const [page, setPage] = useState(0);
  const [detalle, setDetalle] = useState<Producto | null>(null);
  const dq = useDebounce(q);
  const dMin = useDebounce(precioMin);
  const dMax = useDebounce(precioMax);

  const filtros = useFetch(useCallback((s: AbortSignal) => getFiltros(s), []));

  const productos = useFetch(
    useCallback(
      (s: AbortSignal) =>
        buscarProductos(
          {
            q: dq,
            marca,
            departamento,
            categoria,
            subcategoria,
            laboratorio,
            tag,
            conStock,
            precioMin: dMin ? Number(dMin) : undefined,
            precioMax: dMax ? Number(dMax) : undefined,
            page,
            size: PAGE_SIZE,
          },
          s,
        ),
      [dq, marca, departamento, categoria, subcategoria, laboratorio, tag, conStock, dMin, dMax, page],
    ),
    [dq, marca, departamento, categoria, subcategoria, laboratorio, tag, conStock, dMin, dMax, page],
  );

  const f = filtros.data;

  // Cascada: sin departamento elegido se ofrece todo; con uno, solo sus categorías (y lo mismo
  // para subcategoría). Se calcula sobre el árbol, así solo aparecen combinaciones que existen.
  const categorias = useMemo(() => {
    if (!f) return [];
    if (!departamento) return f.categorias;
    const dep = f.taxonomia.find((d) => d.nombre === departamento);
    return dep ? dep.categorias.map((c) => c.nombre) : [];
  }, [f, departamento]);

  const subcategorias = useMemo(() => {
    if (!f) return [];
    const deps = departamento ? f.taxonomia.filter((d) => d.nombre === departamento) : f.taxonomia;
    if (!departamento && !categoria) return f.subcategorias;
    const subs = new Set<string>();
    for (const d of deps) {
      for (const c of d.categorias) {
        if (categoria && c.nombre !== categoria) continue;
        for (const s of c.subcategorias) subs.add(s);
      }
    }
    return [...subs].sort();
  }, [f, departamento, categoria]);

  // Cualquier cambio de filtro vuelve a la primera página.
  function onFiltro<T>(setter: (v: T) => void) {
    return (v: T) => {
      setPage(0);
      setter(v);
    };
  }

  // Cambiar un nivel invalida los de abajo: dejar "SUPLEMENTOS DIETARIOS" seleccionado después de
  // pasar a COSMETICA daría cero resultados sin que se entienda por qué.
  function onDepartamento(v: string) {
    setPage(0);
    setDepartamento(v);
    setCategoria("");
    setSubcategoria("");
  }

  function onCategoria(v: string) {
    setPage(0);
    setCategoria(v);
    setSubcategoria("");
  }

  const data = productos.data;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div>
      <div className="buscador__controls">
        <input
          className="picker__input"
          placeholder="Buscar por nombre, SKU, código de barras o palabra clave…"
          value={q}
          onChange={(e) => onFiltro(setQ)(e.target.value)}
        />
        <div className="buscador__filtros">
          <select value={departamento} onChange={(e) => onDepartamento(e.target.value)}>
            <option value="">Departamento (todos)</option>
            {f?.departamentos.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
          <select value={categoria} onChange={(e) => onCategoria(e.target.value)}>
            <option value="">Categoría (todas)</option>
            {categorias.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <select value={subcategoria} onChange={(e) => onFiltro(setSubcategoria)(e.target.value)}>
            <option value="">Subcategoría (todas)</option>
            {subcategorias.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <select value={laboratorio} onChange={(e) => onFiltro(setLaboratorio)(e.target.value)}>
            <option value="">Laboratorio (todos)</option>
            {f?.laboratorios.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
          <select value={marca} onChange={(e) => onFiltro(setMarca)(e.target.value)}>
            <option value="">Marca (todas)</option>
            {f?.marcas.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
          <label className="buscador__stock">
            <input
              type="checkbox"
              checked={conStock}
              onChange={(e) => onFiltro(setConStock)(e.target.checked)}
            />
            Solo con stock
          </label>
          <input
            type="number"
            className="buscador__precio"
            placeholder="Precio desde"
            min={0}
            value={precioMin}
            onChange={(e) => onFiltro(setPrecioMin)(e.target.value)}
          />
          <input
            type="number"
            className="buscador__precio"
            placeholder="Precio hasta"
            min={0}
            value={precioMax}
            onChange={(e) => onFiltro(setPrecioMax)(e.target.value)}
          />
        </div>
        {tag && (
          <div className="buscador__tag-activo">
            Filtrando por la palabra clave <strong>{tag}</strong>
            <button className="btn btn--sm btn--ghost" onClick={() => onFiltro(setTag)("")}>
              Quitar
            </button>
          </div>
        )}
      </div>

      {productos.loading && <p className="muted">Buscando productos…</p>}
      {productos.error && <div className="alert alert--error">{productos.error}</div>}
      {data && data.content.length === 0 && (
        <p className="muted">Sin productos para esa búsqueda.</p>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="buscador__meta muted">
            {data.totalElements} producto{data.totalElements === 1 ? "" : "s"} · página {page + 1} de{" "}
            {totalPages}
          </div>
          <ul className="prod-list">
            {data.content.map((p) => {
              const added = selectedIds.has(p.id);
              const meta = [p.subcategoria ?? p.categoria, p.laboratorio ?? p.marca]
                .filter(Boolean)
                .join(" · ");
              const tieneInfo = Boolean(p.descripcionWeb || p.imagenUrl || p.tags?.length);
              return (
                <li key={p.id} className="prod-row">
                  {p.imagenUrl ? (
                    <img className="prod-row__thumb" src={p.imagenUrl} alt="" loading="lazy" />
                  ) : (
                    <span className="prod-row__thumb prod-row__thumb--vacio" aria-hidden="true" />
                  )}
                  <div className="prod-row__info">
                    <span className="prod-row__name">{p.nombre}</span>
                    <span className="muted">
                      {meta && <>{meta} · </>}
                      <span className="mono">{p.sku}</span>
                      {p.stock <= 0 && <> · sin stock</>}
                    </span>
                  </div>
                  <span className="prod-row__price">{money(p.precio)}</span>
                  {tieneInfo ? (
                    <button
                      className="btn btn--sm btn--ghost"
                      onClick={() => setDetalle(p)}
                      title="Más información del producto"
                    >
                      Más info
                    </button>
                  ) : (
                    // Ocupa la celda de "Más info" para que el botón Agregar quede alineado con el
                    // del resto de las filas (solo ~3 de cada 4 productos tienen algo que mostrar).
                    <span className="prod-row__spacer" aria-hidden="true" />
                  )}
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

          {totalPages > 1 && (
            <div className="pager">
              <button
                className="btn btn--sm btn--ghost"
                disabled={data.first}
                onClick={() => setPage((n) => Math.max(0, n - 1))}
              >
                ← Anterior
              </button>
              <span className="muted">
                {page + 1} / {totalPages}
              </span>
              <button
                className="btn btn--sm btn--ghost"
                disabled={data.last}
                onClick={() => setPage((n) => n + 1)}
              >
                Siguiente →
              </button>
            </div>
          )}
        </>
      )}

      {detalle && (
        <Modal title={detalle.nombre} onClose={() => setDetalle(null)}>
          <div className="prod-detalle">
            {detalle.imagenUrl && (
              <img className="prod-detalle__img" src={detalle.imagenUrl} alt={detalle.nombre} />
            )}
            <dl className="prod-detalle__datos">
              <div>
                <dt>SKU</dt>
                <dd className="mono">{detalle.sku}</dd>
              </div>
              {detalle.codigoBarras && (
                <div>
                  <dt>Código de barras</dt>
                  <dd className="mono">{detalle.codigoBarras}</dd>
                </div>
              )}
              {detalle.laboratorio && (
                <div>
                  <dt>Laboratorio</dt>
                  <dd>{detalle.laboratorio}</dd>
                </div>
              )}
              {detalle.marca && (
                <div>
                  <dt>Marca</dt>
                  <dd>{detalle.marca}</dd>
                </div>
              )}
              {(detalle.categoria || detalle.subcategoria) && (
                <div>
                  <dt>Categoría</dt>
                  <dd>{[detalle.categoria, detalle.subcategoria].filter(Boolean).join(" · ")}</dd>
                </div>
              )}
            </dl>
            {detalle.descripcionWeb && (
              <p className="prod-detalle__texto">{detalle.descripcionWeb}</p>
            )}
            {detalle.tags && detalle.tags.length > 0 && (
              <div className="prod-detalle__tags">
                {detalle.tags.map((t) => (
                  // Click en un tag = buscar todos los productos con ese tag. Es la forma barata de
                  // navegar "por propiedad" (magnesio, vegano, sin TACC), que el catálogo no tiene
                  // como campo propio.
                  <button
                    key={t}
                    className="tag-chip"
                    onClick={() => {
                      onFiltro(setTag)(t);
                      setDetalle(null);
                    }}
                  >
                    {t}
                  </button>
                ))}
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}
