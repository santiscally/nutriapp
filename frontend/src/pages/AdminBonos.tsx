// F-25 — solapa BONOS del admin: todos los bonos de todas las profesionales, con los mismos
// filtros que tiene la profesional en su listado más el filtro por profesional. Fuente:
// GET /admin/recetas (S-14).
//
// No hay modal de detalle como en el listado de ella: `GET /recetas/{id}` está scopeado al dueño
// del bono y para el admin devuelve 404. Por eso la tabla muestra de una lo que haría falta abrir
// (profesional, comisión y lo que facturó la orden).

import { useCallback, useState } from "react";
import { listarAdminRecetas } from "../api/admin";
import { listarNutricionistas } from "../api/nutricionistas";
import { EmptyState } from "../components/ui/EmptyState";
import { EstadoBadge } from "../components/ui/EstadoBadge";
import { TableSkeleton } from "../components/ui/Skeleton";
import { useDebounce } from "../hooks/useDebounce";
import { useFetch } from "../hooks/useFetch";
import { estadoLabel, fecha, money, pctCorto } from "../lib/format";
import type { EstadoReceta } from "../types/receta";

const PAGE_SIZE = 10;
const ESTADOS: EstadoReceta[] = ["PENDIENTE", "APLICADA", "LIQUIDADA", "VENCIDA", "ANULADA"];

export function AdminBonos() {
  const [estado, setEstado] = useState<EstadoReceta | "">("");
  const [nutricionistaId, setNutricionistaId] = useState("");
  const [q, setQ] = useState("");
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [page, setPage] = useState(0);
  const dq = useDebounce(q);

  // El desplegable de profesionales se puebla con las APROBADAS: son las únicas que pudieron
  // emitir un bono, así que ofrecer el resto sólo agrega filtros que devuelven vacío.
  const { data: profesionales } = useFetch(
    useCallback((s: AbortSignal) => listarNutricionistas({ estado: "APROBADA", size: 200 }, s), []),
  );

  const { data, loading, error } = useFetch(
    useCallback(
      (s: AbortSignal) =>
        listarAdminRecetas(
          { estado, nutricionistaId, q: dq, desde, hasta, page, size: PAGE_SIZE },
          s,
        ),
      [estado, nutricionistaId, dq, desde, hasta, page],
    ),
    [estado, nutricionistaId, dq, desde, hasta, page],
  );

  const hayFiltros = Boolean(estado || nutricionistaId || dq || desde || hasta);

  // Cualquier cambio de filtro vuelve a la página 0: si no, filtrar estando en la página 4 puede
  // dar una tabla vacía con resultados existentes.
  const onFilter =
    <T,>(setter: (v: T) => void) =>
    (v: T) => {
      setter(v);
      setPage(0);
    };

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Bonos</h1>
          <p className="muted">
            {data
              ? `${data.totalElements} bono${data.totalElements === 1 ? "" : "s"} emitido${data.totalElements === 1 ? "" : "s"} en total`
              : "Todos los bonos emitidos en la plataforma"}
          </p>
        </div>
      </div>

      <div className="filtros card">
        <label className="filtros__campo filtros__campo--ancho">
          <span>Buscar</span>
          <input
            className="picker__input"
            placeholder="Código o paciente…"
            value={q}
            onChange={(e) => onFilter(setQ)(e.target.value)}
          />
        </label>
        <label className="filtros__campo">
          <span>Profesional</span>
          <select
            value={nutricionistaId}
            onChange={(e) => onFilter(setNutricionistaId)(e.target.value)}
          >
            <option value="">Todos</option>
            {profesionales?.content.map((n) => (
              <option key={n.id} value={n.id}>
                {n.apellido}, {n.nombre}
              </option>
            ))}
          </select>
        </label>
        <label className="filtros__campo">
          <span>Estado</span>
          <select
            value={estado}
            onChange={(e) => onFilter(setEstado)(e.target.value as EstadoReceta | "")}
          >
            <option value="">Todos</option>
            {ESTADOS.map((s) => (
              <option key={s} value={s}>
                {estadoLabel(s)}
              </option>
            ))}
          </select>
        </label>
        <label className="filtros__campo">
          <span>Desde</span>
          <input type="date" value={desde} onChange={(e) => onFilter(setDesde)(e.target.value)} />
        </label>
        <label className="filtros__campo">
          <span>Hasta</span>
          <input type="date" value={hasta} onChange={(e) => onFilter(setHasta)(e.target.value)} />
        </label>
      </div>

      {loading && <TableSkeleton rows={6} cols={7} />}
      {error && <div className="alert alert--error">{error}</div>}
      {data && data.content.length === 0 && (
        <EmptyState
          icon="clipboard"
          title={hayFiltros ? "Sin bonos para esos filtros" : "Todavía no hay bonos emitidos"}
          hint={hayFiltros ? "Ajustá los filtros." : undefined}
        />
      )}

      {data && data.content.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Profesional</th>
                <th>Paciente</th>
                <th>Estado</th>
                <th>Emitido</th>
                {/* El admin sí ve lo que facturó la orden: es con lo que liquida (C-06). */}
                <th className="ta-right">Facturado</th>
                <th className="ta-right">Comisión</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((r) => (
                <tr key={r.id}>
                  <td className="mono">{r.codigo}</td>
                  <td>
                    {r.nutricionista.nombre} {r.nutricionista.apellido}
                    <br />
                    <span className="muted">{r.nutricionista.email}</span>
                  </td>
                  <td>
                    {r.paciente.nombre} {r.paciente.apellido}
                  </td>
                  <td>
                    <EstadoBadge estado={r.estado} />
                    {r.conversion?.liquidadaAt && (
                      <>
                        <br />
                        <span className="muted">liquidado</span>
                      </>
                    )}
                  </td>
                  <td className="muted">
                    {fecha(r.emitidaAt)}
                    <br />
                    <span className="muted">vence {fecha(r.venceAt)}</span>
                  </td>
                  <td className="ta-right">
                    {r.conversion ? money(r.conversion.ordenTotal) : <span className="muted">—</span>}
                  </td>
                  <td className="ta-right">
                    {r.conversion ? (
                      <>
                        {money(r.conversion.comisionMonto)}
                        <br />
                        <span className="muted">{pctCorto(r.conversion.comisionPct)}%</span>
                      </>
                    ) : (
                      <span className="muted">—</span>
                    )}
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
              ← Anterior
            </button>
            <span className="muted">
              Página {data.page + 1} de {data.totalPages} · {data.totalElements} bonos
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
    </section>
  );
}
