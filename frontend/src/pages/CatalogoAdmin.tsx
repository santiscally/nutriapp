// Catálogo visto por el admin. Es el reverso del buscador de recetas: ahí se ve lo que se puede
// recetar, acá lo que NO y por qué.
//
// El caso que motivó la pantalla: después de importar el maestro quedaron 62 artículos sin match
// contra Contabilium. Ese número aparecía en el reporte del import y se perdía al cerrar el modal;
// sin poder listarlos, "62 sin match" no era accionable. Ahora son un filtro.

import { useCallback, useState } from "react";
import { getCatalogoResumen, listarProductosAdmin } from "../api/productos";
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

export function CatalogoAdmin() {
  const [q, setQ] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("todos");
  const [page, setPage] = useState(0);
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
            Todo lo que bajó de Contabilium, incluido lo que no se puede recetar. El catálogo se
            actualiza desde <strong>Integraciones</strong>.
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
                <th>Sincronizado</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((p) => (
                <tr key={p.producto.id}>
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
                  <td className="muted" style={{ fontSize: "0.8rem" }}>
                    {p.lastSyncedAt ? fecha(p.lastSyncedAt) : "—"}
                  </td>
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
    </section>
  );
}
