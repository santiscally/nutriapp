// Buscador de productos del emisor de recetas.
//
// `q` va contra nombre, SKU, descripción, código de barras y los tags del maestro, y el backend
// devuelve el resultado rankeado (nombre → descripción → tag). Los filtros de departamento,
// categoría y subcategoría se encadenan con el árbol que manda /productos/filtros: sueltas, las
// subcategorías son 142 opciones en un dropdown y no las usa nadie.
//
// Los productos que no están en el maestro de TBC no tienen ni taxonomía ni imagen ni tags: la fila
// tiene que verse bien igual (solo 1 de cada 4 tiene imagen).

import { useCallback, useEffect, useMemo, useState } from "react";
import { buscarProductos, getFiltros } from "../../api/productos";
import { useDebounce } from "../../hooks/useDebounce";
import { useFetch } from "../../hooks/useFetch";
import { money } from "../../lib/format";
import type { Producto } from "../../types/producto";
import { Icon } from "../ui/Icon";
import { Modal } from "../ui/Modal";
import { RangoPrecio } from "./RangoPrecio";

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
  // null = el extremo del catálogo, o sea "sin filtrar por ese lado".
  const [precioDesde, setPrecioDesde] = useState<number | null>(null);
  const [precioHasta, setPrecioHasta] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [detalle, setDetalle] = useState<Producto | null>(null);
  const [panelAbierto, setPanelAbierto] = useState(false);
  const dq = useDebounce(q);
  const dMin = useDebounce(precioDesde);
  const dMax = useDebounce(precioHasta);

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
            precioMin: dMin ?? undefined,
            precioMax: dMax ?? undefined,
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

  // Extremos reales del catálogo. Mientras no lleguen, el slider no se dibuja (no hay escala).
  const catalogoMin = f?.precioMin ?? null;
  const catalogoMax = f?.precioMax ?? null;
  const hayRango = catalogoMin != null && catalogoMax != null && catalogoMax > catalogoMin;

  // Si el catálogo cambia de rango (re-sync), un filtro viejo puede quedar fuera de escala.
  useEffect(() => {
    if (!hayRango) return;
    setPrecioDesde((v) => (v != null && v < catalogoMin! ? catalogoMin : v));
    setPrecioHasta((v) => (v != null && v > catalogoMax! ? catalogoMax : v));
  }, [hayRango, catalogoMin, catalogoMax]);

  // Chips de lo que está filtrando ahora mismo: con el panel cerrado es lo único que dice por qué
  // la lista trae 12 resultados y no 700.
  const activos: { label: string; quitar: () => void }[] = [];
  if (departamento) activos.push({ label: departamento, quitar: () => onDepartamento("") });
  if (categoria) activos.push({ label: categoria, quitar: () => onCategoria("") });
  if (subcategoria) activos.push({ label: subcategoria, quitar: () => onFiltro(setSubcategoria)("") });
  if (laboratorio) activos.push({ label: laboratorio, quitar: () => onFiltro(setLaboratorio)("") });
  if (marca) activos.push({ label: marca, quitar: () => onFiltro(setMarca)("") });
  if (conStock) activos.push({ label: "Con stock", quitar: () => onFiltro(setConStock)(false) });
  if (tag) activos.push({ label: `#${tag}`, quitar: () => onFiltro(setTag)("") });
  if (precioDesde != null || precioHasta != null) {
    activos.push({
      label: `${money(precioDesde ?? catalogoMin ?? 0)} – ${money(precioHasta ?? catalogoMax ?? 0)}`,
      quitar: () => {
        setPage(0);
        setPrecioDesde(null);
        setPrecioHasta(null);
      },
    });
  }

  function limpiarTodo() {
    setPage(0);
    setDepartamento("");
    setCategoria("");
    setSubcategoria("");
    setLaboratorio("");
    setMarca("");
    setConStock(false);
    setTag("");
    setPrecioDesde(null);
    setPrecioHasta(null);
  }

  const data = productos.data;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="buscador">
      <div className="buscador__controls">
        <div className="buscador__barra">
          <input
            className="picker__input"
            placeholder="Buscar por nombre, SKU, código de barras o palabra clave…"
            value={q}
            onChange={(e) => onFiltro(setQ)(e.target.value)}
          />
          {/* Los 8 controles de filtro estaban siempre a la vista y tapaban la lista, que es lo
              que la nutricionista viene a leer. Ahora se despliegan, y los chips de abajo dejan
              ver qué hay aplicado sin abrir nada. */}
          <button
            type="button"
            className={"btn btn--ghost buscador__toggle" + (panelAbierto ? " buscador__toggle--on" : "")}
            onClick={() => setPanelAbierto((v) => !v)}
            aria-expanded={panelAbierto}
          >
            <Icon name="search" size={15} />
            Filtros
            {activos.length > 0 && <span className="buscador__contador">{activos.length}</span>}
          </button>
        </div>

        {activos.length > 0 && (
          <div className="buscador__chips">
            {activos.map((a) => (
              <button key={a.label} className="chip" onClick={a.quitar} title="Quitar este filtro">
                {a.label}
                <span aria-hidden="true">×</span>
              </button>
            ))}
            <button className="chip chip--limpiar" onClick={limpiarTodo}>
              Limpiar todo
            </button>
          </div>
        )}

        {panelAbierto && (
          <div className="buscador__panel">
            <div className="buscador__filtros">
              <label className="buscador__campo">
                <span>Departamento</span>
                <select value={departamento} onChange={(e) => onDepartamento(e.target.value)}>
                  <option value="">Todos</option>
                  {f?.departamentos.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </label>
              <label className="buscador__campo">
                <span>Categoría</span>
                <select value={categoria} onChange={(e) => onCategoria(e.target.value)}>
                  <option value="">Todas</option>
                  {categorias.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </label>
              <label className="buscador__campo">
                <span>Subcategoría</span>
                <select
                  value={subcategoria}
                  onChange={(e) => onFiltro(setSubcategoria)(e.target.value)}
                >
                  <option value="">Todas</option>
                  {subcategorias.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
              <label className="buscador__campo">
                <span>Laboratorio</span>
                <select value={laboratorio} onChange={(e) => onFiltro(setLaboratorio)(e.target.value)}>
                  <option value="">Todos</option>
                  {f?.laboratorios.map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label className="buscador__campo">
                <span>Marca</span>
                <select value={marca} onChange={(e) => onFiltro(setMarca)(e.target.value)}>
                  <option value="">Todas</option>
                  {f?.marcas.map((m) => (
                    <option key={m} value={m}>
                      {m}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="buscador__panel-pie">
              {hayRango && (
                <RangoPrecio
                  min={catalogoMin!}
                  max={catalogoMax!}
                  desde={precioDesde ?? catalogoMin!}
                  hasta={precioHasta ?? catalogoMax!}
                  formato={money}
                  onChange={(d, h) => {
                    setPage(0);
                    // Pegado a un extremo = sin filtro por ese lado: así el chip no miente
                    // diciendo que hay un rango aplicado cuando abarca todo el catálogo.
                    setPrecioDesde(d <= catalogoMin! ? null : d);
                    setPrecioHasta(h >= catalogoMax! ? null : h);
                  }}
                />
              )}
              <label className="buscador__stock">
                <input
                  type="checkbox"
                  checked={conStock}
                  onChange={(e) => onFiltro(setConStock)(e.target.checked)}
                />
                Solo con stock
              </label>
            </div>
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
        <Modal title={detalle.nombre} onClose={() => setDetalle(null)} ancho>
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
