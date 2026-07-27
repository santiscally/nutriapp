// F.5 — Recetas. Lista con filtros (estado/q/desde/hasta) + paginación + detalle (modal) con anular/reenviar.

import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { listarRecetas } from "../api/recetas";
import { RecetaDetalle } from "../components/receta/RecetaDetalle";
import { EmptyState } from "../components/ui/EmptyState";
import { EstadoBadge } from "../components/ui/EstadoBadge";
import { Icon } from "../components/ui/Icon";
import { TableSkeleton } from "../components/ui/Skeleton";
import { useDebounce } from "../hooks/useDebounce";
import { useFetch } from "../hooks/useFetch";
import { fecha } from "../lib/format";
import type { EstadoReceta } from "../types/receta";

const PAGE_SIZE = 10;
const ESTADOS: EstadoReceta[] = ["PENDIENTE", "APLICADA", "VENCIDA", "ANULADA"];

export function Recetas() {
  const [estado, setEstado] = useState<EstadoReceta | "">("");
  const [q, setQ] = useState("");
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [page, setPage] = useState(0);
  const [detalleId, setDetalleId] = useState<string | null>(null);
  const dq = useDebounce(q);

  const fetcher = useCallback(
    (s: AbortSignal) =>
      listarRecetas({ estado, q: dq, desde, hasta, page, size: PAGE_SIZE }, s),
    [estado, dq, desde, hasta, page],
  );
  const { data, loading, error, refetch } = useFetch(fetcher, [
    estado,
    dq,
    desde,
    hasta,
    page,
  ]);

  const hasFilters = Boolean(estado || dq || desde || hasta);

  // cualquier cambio de filtro vuelve a la página 0
  const onFilter = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Recetas</h1>
          <p className="muted">
            {data
              ? `${data.totalElements} receta${data.totalElements === 1 ? "" : "s"} emitida${data.totalElements === 1 ? "" : "s"}`
              : "Historial de recetas emitidas"}
          </p>
        </div>
        <Link className="btn btn--primary" to="/recetas/nueva">
          <Icon name="file-plus" />
          Emitir receta
        </Link>
      </div>

      <div className="filtros card">
        <input
          className="picker__input"
          placeholder="Buscar por código o paciente…"
          value={q}
          onChange={(e) => onFilter(setQ)(e.target.value)}
        />
        <select value={estado} onChange={(e) => onFilter(setEstado)(e.target.value as EstadoReceta | "")}>
          <option value="">Estado (todos)</option>
          {ESTADOS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <label className="filtros__date">
          Desde
          <input type="date" value={desde} onChange={(e) => onFilter(setDesde)(e.target.value)} />
        </label>
        <label className="filtros__date">
          Hasta
          <input type="date" value={hasta} onChange={(e) => onFilter(setHasta)(e.target.value)} />
        </label>
      </div>

      {loading && <TableSkeleton rows={6} cols={5} />}
      {error && <div className="alert alert--error">{error}</div>}
      {data && data.content.length === 0 && (
        <EmptyState
          icon="clipboard"
          title={hasFilters ? "Sin recetas para esos filtros" : "Todavía no emitiste recetas"}
          hint={hasFilters ? "Ajustá los filtros." : undefined}
          action={
            !hasFilters && (
              <Link className="btn btn--primary" to="/recetas/nueva">
                <Icon name="file-plus" />
                Emitir receta
              </Link>
            )
          }
        />
      )}

      {data && data.content.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Paciente</th>
                <th>Estado</th>
                <th>Emitida</th>
                <th>Vence</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((r) => (
                <tr key={r.id} className="row-click" onClick={() => setDetalleId(r.id)}>
                  <td className="mono">{r.codigo}</td>
                  <td>
                    {r.paciente.nombre} {r.paciente.apellido}
                  </td>
                  <td>
                    <EstadoBadge estado={r.estado} />
                  </td>
                  <td className="muted">{fecha(r.emitidaAt)}</td>
                  <td className="muted">{fecha(r.venceAt)}</td>
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
              ← Anterior
            </button>
            <span className="muted">
              Página {data.page + 1} de {data.totalPages} · {data.totalElements} recetas
            </span>
            <button
              className="btn btn--sm btn--ghost"
              disabled={data.last}
              onClick={() => setPage((n) => n + 1)}
            >
              Siguiente →
            </button>
          </div>
        </>
      )}

      {detalleId && (
        <RecetaDetalle id={detalleId} onClose={() => setDetalleId(null)} onChanged={refetch} />
      )}
    </section>
  );
}
