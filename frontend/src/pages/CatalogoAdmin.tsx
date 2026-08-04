// Catálogo visto por el admin. Es el reverso del buscador de recetas: ahí se ve lo que se puede
// recetar, acá lo que NO y por qué.
//
// El caso que motivó la pantalla: después de importar el maestro quedaron artículos sin match
// contra Contabilium. Ese número aparecía en el reporte del import y se perdía al cerrar el modal;
// sin poder listarlos, "104 sin match" no era accionable. Ahora son un filtro.
//
// El detalle de cada producto (incluidos los tags del maestro) se abre en un modal. Se probó como
// fila expandible y se descartó: al abrirse empuja todas las filas de abajo, la tabla salta y se
// pierde de vista lo que se venía leyendo. Es sólo lectura: el catálogo lo escriben el sync de
// Contabilium y el import del Excel, no esta pantalla.

import { useCallback, useState, type ReactNode } from "react";
import { getCatalogoResumen, listarProductosAdmin } from "../api/productos";
import { Modal } from "../components/ui/Modal";
import { useDebounce } from "../hooks/useDebounce";
import { useFetch } from "../hooks/useFetch";
import { fecha, money } from "../lib/format";
import type { ProductoAdmin } from "../types/producto";

type Filtro = "todos" | "sinMaestro" | "noPublicados";

const FILTROS: { key: Filtro; label: string }[] = [
  { key: "todos", label: "Todos" },
  { key: "sinMaestro", label: "Sin match del maestro" },
  { key: "noPublicados", label: "No recetables" },
];

function Etiquetas({ p }: { p: ProductoAdmin }) {
  return (
    <span className="labels">
      {p.producto.publicado ? (
        <span className="badge badge--ok">Recetable</span>
      ) : (
        <span className="badge badge--off" title={p.motivoNoPublicado ?? undefined}>
          {p.motivoNoPublicado ?? "No recetable"}
        </span>
      )}
      {!p.enMaestro && (
        <span
          className="badge badge--warn"
          title="Está en Contabilium pero no en el Excel maestro: se receta sin categoría, laboratorio, imagen ni tags."
        >
          Sin maestro
        </span>
      )}
      {p.bloqueadoMaestro && <span className="badge badge--off">Bloqueado</span>}
    </span>
  );
}

/** Detalle que se abre debajo de la fila. Sólo visualización. */
function Detalle({ p }: { p: ProductoAdmin }) {
  const prod = p.producto;
  const datos: { label: string; valor: ReactNode }[] = [
    { label: "Código de barras", valor: prod.codigoBarras || "—" },
    { label: "Departamento", valor: prod.departamento || "—" },
    { label: "Subcategoría", valor: prod.subcategoria || "—" },
    { label: "Laboratorio", valor: prod.laboratorio || "—" },
    { label: "Rubro (ERP)", valor: p.rubro || "—" },
    { label: "Tipo (ERP)", valor: p.tipoErp || "—" },
    { label: "Estado en el ERP", valor: p.activoErp ? "Activo" : "Inactivo" },
    { label: "Último sync", valor: p.lastSyncedAt ? fecha(p.lastSyncedAt) : "nunca" },
    {
      label: "Maestro",
      valor: p.maestroSyncedAt ? `importado el ${fecha(p.maestroSyncedAt)}` : "sin match",
    },
  ];

  return (
    <div className="prod-admin">
      {prod.imagenUrl && (
        <img className="prod-admin__img" src={prod.imagenUrl} alt="" loading="lazy" />
      )}

      <div className="prod-admin__cuerpo">
        <dl className="prod-admin__datos">
          {datos.map((d) => (
            <div key={d.label}>
              <dt>{d.label}</dt>
              <dd>{d.valor}</dd>
            </div>
          ))}
        </dl>

        {prod.descripcionWeb && <p className="prod-admin__texto">{prod.descripcionWeb}</p>}

        {/* Los tags son la vía de búsqueda por propiedad ("magnesio", "vegano", "sin TACC"): el
            catálogo no tiene esos campos como columnas, los cubre el maestro con esta lista. Acá
            van sólo para ver qué quedó cargado — en el buscador de recetas sí son clickeables. */}
        <div className="prod-admin__tags">
          <span className="prod-admin__tags-label">
            Tags del maestro
            {prod.tags && prod.tags.length > 0 ? ` (${prod.tags.length})` : ""}
          </span>
          {prod.tags && prod.tags.length > 0 ? (
            <div className="burbujas">
              {prod.tags.map((tag) => (
                <span key={tag} className="burbuja">
                  {tag}
                </span>
              ))}
            </div>
          ) : (
            <p className="prod-admin__sin-tags">
              {p.enMaestro
                ? "El maestro lo tocó pero no le cargó tags."
                : "No está en el maestro: sin tags no se lo encuentra por palabra clave."}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export function CatalogoAdmin() {
  const [q, setQ] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("todos");
  const [page, setPage] = useState(0);
  const [detalle, setDetalle] = useState<ProductoAdmin | null>(null);
  const qDebounced = useDebounce(q, 300);

  const resumenFetcher = useCallback((s: AbortSignal) => getCatalogoResumen(s), []);
  const { data: resumen } = useFetch(resumenFetcher);

  const fetcher = useCallback(
    (s: AbortSignal) =>
      listarProductosAdmin(
        {
          q: qDebounced,
          sinMaestro: filtro === "sinMaestro",
          publicado: filtro === "noPublicados" ? false : undefined,
          page,
        },
        s,
      ),
    [qDebounced, filtro, page],
  );
  const { data, loading, error } = useFetch(fetcher, [qDebounced, filtro, page]);

  function cambiarFiltro(f: Filtro) {
    setFiltro(f);
    setPage(0);
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Productos</h1>
          <p className="muted">
            Todo lo que bajó de Contabilium, incluido lo que no se puede recetar. Tocá una fila para
            ver el detalle. El catálogo se actualiza desde <strong>Integraciones</strong>.
          </p>
        </div>
      </div>

      {resumen && (
        <div className="tiles">
          <div className="card tile">
            <span className="tile__body">
              <span className="tile__label">En el catálogo</span>
              <span className="tile__value">{resumen.total}</span>
            </span>
          </div>
          <div className="card tile">
            <span className="tile__body">
              <span className="tile__label">Recetables</span>
              <span className="tile__value">{resumen.publicados}</span>
            </span>
          </div>
          <div className="card tile">
            <span className="tile__body">
              <span className="tile__label">Sin match del maestro</span>
              <span className="tile__value">{resumen.sinMaestro}</span>
              <span className="muted" style={{ fontSize: "0.78rem" }}>
                están en el ERP, no en el Excel
              </span>
            </span>
          </div>
          <div className="card tile">
            <span className="tile__body">
              <span className="tile__label">Bloqueados en el maestro</span>
              <span className="tile__value">{resumen.bloqueados}</span>
            </span>
          </div>
        </div>
      )}

      <div className="filtros-bar">
        <input
          className="filtros-bar__search"
          placeholder="Buscar por nombre, SKU o código de barras…"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
        />
        <div className="tabs">
          {FILTROS.map((f) => (
            <button
              key={f.key}
              className={"tab" + (filtro === f.key ? " tab--active" : "")}
              onClick={() => cambiarFiltro(f.key)}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {loading && <p className="muted">Cargando…</p>}
      {error && <div className="alert alert--error">{error}</div>}
      {data && data.content.length === 0 && !loading && (
        <p className="muted">No hay productos que cumplan ese criterio.</p>
      )}

      {data && data.content.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Producto</th>
                <th>SKU</th>
                <th>Categoría</th>
                <th className="ta-right">Precio</th>
                <th className="ta-right">Stock</th>
                <th>Estado</th>
                <th className="ta-right">Tags</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((p) => (
                <tr key={p.producto.id} className="row-click" onClick={() => setDetalle(p)}>
                  <td>
                    <strong>{p.producto.nombre}</strong>
                    {p.producto.marca && (
                      <>
                        <br />
                        <span className="muted" style={{ fontSize: "0.8rem" }}>
                          {p.producto.marca}
                        </span>
                      </>
                    )}
                  </td>
                  <td className="mono">{p.producto.sku}</td>
                  <td className="muted">{p.producto.categoria || "—"}</td>
                  <td className="ta-right">{money(p.producto.precio)}</td>
                  <td className="ta-right">{p.producto.stock}</td>
                  <td>
                    <Etiquetas p={p} />
                  </td>
                  <td className="ta-right muted">{p.producto.tags?.length || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="pager">
            <button
              className="btn btn--sm btn--ghost"
              disabled={data.first}
              onClick={() => setPage((n) => Math.max(0, n - 1))}
            >
              Anterior
            </button>
            <span className="muted">
              Página {data.page + 1} de {data.totalPages} · {data.totalElements} productos
            </span>
            <button
              className="btn btn--sm btn--ghost"
              disabled={data.last}
              onClick={() => setPage((n) => n + 1)}
            >
              Siguiente
            </button>
          </div>
        </>
      )}

      {detalle && (
        <Modal title={detalle.producto.nombre} onClose={() => setDetalle(null)} ancho>
          <Detalle p={detalle} />
        </Modal>
      )}
    </section>
  );
}
