// F.5 — Recetas. Lista con filtros (estado/q/desde/hasta) + paginación + detalle (modal) con anular/reenviar.

import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { ApiRequestError } from "../api/client";
import { anularReceta, listarRecetas, reenviarReceta } from "../api/recetas";
import { RecetaDetalle } from "../components/receta/RecetaDetalle";
import { EmptyState } from "../components/ui/EmptyState";
import { EstadoBadge } from "../components/ui/EstadoBadge";
import { Icon } from "../components/ui/Icon";
import { TableSkeleton } from "../components/ui/Skeleton";
import { useDialog } from "../components/ui/Dialog";
import { useToast } from "../components/ui/Toast";
import { useDebounce } from "../hooks/useDebounce";
import { useFetch } from "../hooks/useFetch";
import { fecha } from "../lib/format";
import type { EstadoReceta, RecetaResponse } from "../types/receta";

const PAGE_SIZE = 10;
const ESTADOS: EstadoReceta[] = ["PENDIENTE", "APLICADA", "LIQUIDADA", "VENCIDA", "ANULADA"];

export function Recetas() {
  const [estado, setEstado] = useState<EstadoReceta | "">("");
  const [q, setQ] = useState("");
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [page, setPage] = useState(0);
  const [detalleId, setDetalleId] = useState<string | null>(null);
  // id de la fila con una acción en vuelo: evita el doble click sin bloquear toda la tabla.
  const [busyId, setBusyId] = useState<string | null>(null);
  const dq = useDebounce(q);
  const toast = useToast();
  const { confirmar } = useDialog();

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

  // Las acciones viven dentro de una fila clickeable: sin stopPropagation, cada click abriría
  // también el detalle por detrás del diálogo de confirmación.
  async function accion(
    e: React.MouseEvent,
    r: RecetaResponse,
    fn: (id: string) => Promise<unknown>,
    confirmacion: { titulo: string; mensaje: string; confirmar: string; peligro?: boolean },
    okMsg: string,
  ) {
    e.stopPropagation();
    if (!(await confirmar(confirmacion))) return;
    setBusyId(r.id);
    try {
      await fn(r.id);
      toast.success(okMsg);
      refetch();
    } catch (err) {
      toast.error(
        err instanceof ApiRequestError ? err.message : "No se pudo completar la acción.",
      );
    } finally {
      setBusyId(null);
    }
  }

  // cualquier cambio de filtro vuelve a la página 0
  const onFilter = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Bonos profesionales</h1>
          <p className="muted">
            {data
              ? `${data.totalElements} bono${data.totalElements === 1 ? "" : "s"} emitido${data.totalElements === 1 ? "" : "s"}`
              : "Historial de bonos emitidos"}
          </p>
        </div>
        <Link className="btn btn--primary" to="/recetas/nueva">
          <Icon name="file-plus" />
          Emitir bono
        </Link>
      </div>

      {/* Los cuatro controles comparten estructura (label arriba + control abajo) y alto: antes el
          buscador y el select iban pelados y las fechas dentro de un label con texto, así que
          quedaban más bajos y corridos respecto de los otros dos. */}
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
          <span>Estado</span>
          <select
            value={estado}
            onChange={(e) => onFilter(setEstado)(e.target.value as EstadoReceta | "")}
          >
            <option value="">Todos</option>
            {ESTADOS.map((s) => (
              <option key={s} value={s}>
                {s}
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

      {loading && <TableSkeleton rows={6} cols={5} />}
      {error && <div className="alert alert--error">{error}</div>}
      {data && data.content.length === 0 && (
        <EmptyState
          icon="clipboard"
          title={hasFilters ? "Sin bonos para esos filtros" : "Todavía no emitiste bonos"}
          hint={hasFilters ? "Ajustá los filtros." : undefined}
          action={
            !hasFilters && (
              <Link className="btn btn--primary" to="/recetas/nueva">
                <Icon name="file-plus" />
                Emitir bono
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
                <th className="table__actions">Acciones</th>
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
                  {/* Sólo un bono PENDIENTE se puede mandar o anular; en el resto la celda va
                      vacía en vez de con tres botones grises, que serían ruido en cada fila. */}
                  <td className="table__actions">
                    {r.estado === "PENDIENTE" && (
                      <>
                        {r.waMeUrl && (
                          <a
                            className="btn-icon"
                            href={r.waMeUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            title="Enviar por WhatsApp"
                            aria-label={`Enviar el bono ${r.codigo} por WhatsApp`}
                            onClick={(e) => e.stopPropagation()}
                          >
                            <Icon name="whatsapp" size={16} />
                          </a>
                        )}
                        <button
                          className="btn-icon"
                          disabled={busyId === r.id}
                          title="Reenviar por mail"
                          aria-label={`Reenviar el bono ${r.codigo} por mail`}
                          onClick={(e) =>
                            accion(
                              e,
                              r,
                              reenviarReceta,
                              {
                                titulo: "¿Reenviar el mail?",
                                mensaje: `Se vuelve a encolar el mail con el código ${r.codigo} a ${r.paciente.email}.`,
                                confirmar: "Reenviar",
                              },
                              "Mail reenviado.",
                            )
                          }
                        >
                          <Icon name="mail" size={16} />
                        </button>
                        <button
                          className="btn-icon btn-icon--danger"
                          disabled={busyId === r.id}
                          title="Anular bono"
                          aria-label={`Anular el bono ${r.codigo}`}
                          onClick={(e) =>
                            accion(
                              e,
                              r,
                              anularReceta,
                              {
                                titulo: `¿Anular el bono ${r.codigo}?`,
                                mensaje:
                                  "El cupón se da de baja en la tienda y la paciente ya no va a poder usarlo. " +
                                  "No se puede deshacer.",
                                confirmar: "Anular bono",
                                peligro: true,
                              },
                              "Bono anulado.",
                            )
                          }
                        >
                          <Icon name="ban" size={16} />
                        </button>
                      </>
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

      {detalleId && (
        <RecetaDetalle id={detalleId} onClose={() => setDetalleId(null)} onChanged={refetch} />
      )}
    </section>
  );
}
