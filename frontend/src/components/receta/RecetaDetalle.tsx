// Detalle de receta (modal): items, notificaciones, conversión + acciones anular/reenviar (solo PENDIENTE).

import { useCallback, useState } from "react";
import { ApiRequestError } from "../../api/client";
import { anularReceta, getReceta, reenviarReceta } from "../../api/recetas";
import { useFetch } from "../../hooks/useFetch";
import { fecha, fechaHora, money } from "../../lib/format";
import { useDialog } from "../ui/Dialog";
import { Icon } from "../ui/Icon";
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
  const { confirmar } = useDialog();

  async function run(
    fn: (id: string) => Promise<unknown>,
    confirmacion: { titulo: string; mensaje: string; confirmar: string; peligro?: boolean },
    okMsg: string,
  ) {
    if (!(await confirmar(confirmacion))) return;
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
    <Modal title={data ? `Bono ${data.codigo}` : "Bono"} onClose={onClose}>
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

          {/* C-02: la receta emitida no lleva precios. El único importe que se muestra es la
              comisión de la conversión, más abajo — no lo que la tienda facturó. */}
          <h3 className="detalle__title">Productos</h3>
          <ul className="detalle__items">
            {data.items.map((it, i) => (
              <li key={i}>
                <span>
                  {it.cantidad > 1 ? `${it.cantidad}× ` : ""}
                  {it.producto.nombre}
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
                Orden #{data.conversion.ordenNumero} · tu comisión ({data.conversion.comisionPct}%):{" "}
                <strong>{money(data.conversion.comisionMonto)}</strong>
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
              {/* 2.4: "Reenviar" sólo reencola el mail. El WhatsApp lo manda la nutricionista
                  desde su teléfono, así que acá va el link, no un botón que dispare un envío. */}
              {data.waMeUrl && (
                <a
                  className="btn btn--sm btn--whatsapp"
                  href={data.waMeUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <Icon name="whatsapp" size={16} />
                  Enviar por WhatsApp
                </a>
              )}
              <button
                className="btn btn--sm btn--ghost"
                disabled={busy}
                onClick={() =>
                  run(
                    reenviarReceta,
                    {
                      titulo: "¿Reenviar el mail?",
                      mensaje: `Se vuelve a encolar el mail con el código ${data.codigo} a ${data.paciente.email}.`,
                      confirmar: "Reenviar",
                    },
                    "Mail reenviado.",
                  )
                }
              >
                <Icon name="mail" size={16} />
                Reenviar mail
              </button>
              <button
                className="btn btn--sm btn--danger"
                disabled={busy}
                onClick={() =>
                  run(
                    anularReceta,
                    {
                      titulo: `¿Anular el bono ${data.codigo}?`,
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
                Anular
              </button>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
