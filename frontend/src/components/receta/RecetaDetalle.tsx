// Detalle de receta (modal): items, notificaciones, conversión + acciones anular/reenviar (solo PENDIENTE).

import { useCallback, useState } from "react";
import { ApiRequestError } from "../../api/client";
import { anularReceta, getReceta, reenviarReceta } from "../../api/recetas";
import { useFetch } from "../../hooks/useFetch";
import { fecha, fechaHora, money } from "../../lib/format";
import { EstadoBadge } from "../ui/EstadoBadge";
import { Modal } from "../ui/Modal";
import { useToast } from "../ui/Toast";

interface Props {
  id: string;
  onClose: () => void;
  onChanged: () => void; // refrescar la lista tras anular/reenviar
}

export function RecetaDetalle({ id, onClose, onChanged }: Props) {
  const fetcher = useCallback((s: AbortSignal) => getReceta(id, s), [id]);
  const { data, loading, error, refetch } = useFetch(fetcher, [id]);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  async function run(
    fn: (id: string) => Promise<unknown>,
    confirmMsg: string,
    okMsg: string,
  ) {
    if (!window.confirm(confirmMsg)) return;
    setBusy(true);
    try {
      await fn(id);
      toast.success(okMsg);
      refetch();
      onChanged();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "No se pudo completar la acción.");
    } finally {
      setBusy(false);
    }
  }

  const esPendiente = data?.estado === "PENDIENTE";

  return (
    <Modal title={data ? `Receta ${data.codigo}` : "Receta"} onClose={onClose}>
      {loading && <p className="muted">Cargando…</p>}
      {error && <div className="alert alert--error">{error}</div>}

      {data && (
        <div className="detalle">
          <div className="detalle__head">
            <EstadoBadge estado={data.estado} />
            <span className="muted">
              Emitida {fechaHora(data.emitidaAt)} · vence {fecha(data.venceAt)}
            </span>
          </div>

          <dl className="detalle__meta">
            <div>
              <dt>Paciente</dt>
              <dd>
                {data.paciente.nombre} {data.paciente.apellido}
                <br />
                <span className="muted">
                  {data.paciente.email} · {data.paciente.whatsapp}
                </span>
              </dd>
            </div>
            <div>
              <dt>Descuento</dt>
              <dd>{data.descuentoPct}%</dd>
            </div>
            <div>
              <dt>Cupón</dt>
              <dd>{data.cuponSyncEstado}</dd>
            </div>
          </dl>

          {/* C-02: la receta emitida no lleva precios. El único importe real es el de la
              conversión (lo que el paciente pagó en TiendaNube), más abajo. */}
          <h3 className="detalle__title">Productos</h3>
          <ul className="detalle__items">
            {data.items.map((it, i) => (
              <li key={i}>
                <span>
                  {it.cantidad}× {it.producto.nombre}
                  {it.indicaciones ? <em className="muted"> — {it.indicaciones}</em> : null}
                </span>
              </li>
            ))}
          </ul>

          <h3 className="detalle__title">Notificaciones</h3>
          {data.notificaciones && data.notificaciones.length > 0 ? (
            <ul className="detalle__notifs">
              {data.notificaciones.map((n, i) => (
                <li key={i}>
                  <span>{n.canal}</span>
                  <span className="muted">{n.estado}</span>
                  <span className="muted">{n.sentAt ? fechaHora(n.sentAt) : "—"}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">Sin notificaciones registradas.</p>
          )}

          {data.conversion && (
            <>
              <h3 className="detalle__title">Conversión</h3>
              <p>
                Orden #{data.conversion.ordenNumero} · {money(data.conversion.ordenTotal)} · comisión{" "}
                {data.conversion.comisionPct}% = <strong>{money(data.conversion.comisionMonto)}</strong>
              </p>
              <p className="muted">
                {data.conversion.liquidadaAt
                  ? `Comisión liquidada el ${fecha(data.conversion.liquidadaAt)}.`
                  : "Comisión pendiente de liquidación."}
              </p>
            </>
          )}

          {esPendiente && (
            <div className="detalle__actions">
              <button
                className="btn btn--sm btn--ghost"
                disabled={busy}
                onClick={() =>
                  run(
                    reenviarReceta,
                    "¿Reenviar las notificaciones de esta receta?",
                    "Notificaciones reenviadas.",
                  )
                }
              >
                Reenviar
              </button>
              <button
                className="btn btn--sm btn--danger"
                disabled={busy}
                onClick={() =>
                  run(
                    anularReceta,
                    "¿Anular esta receta? Se intentará borrar el cupón.",
                    "Receta anulada.",
                  )
                }
              >
                Anular
              </button>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
