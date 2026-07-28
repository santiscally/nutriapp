// Panel de integraciones (solo ADMIN): estado de los servicios externos + acciones manuales de
// recuperación (resiliencia 2.7–2.9). GET /admin/integraciones/estado + POST resync-cupones /
// sync-productos. En stub todo degrada con mensaje explícito (el 503 de Contabilium se surfacea tal cual).

import { useCallback, useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { ApiRequestError } from "../api/client";
import { getIntegracionesEstado, resyncCupones, syncProductos } from "../api/integraciones";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../components/ui/Toast";
import { fechaHora } from "../lib/format";
import type { IntegracionEstado } from "../types/integraciones";

const NOMBRE: Record<string, string> = {
  contabilium: "Contabilium · ERP",
  tiendanube: "TiendaNube · tienda",
  mail: "Email",
  whatsapp: "WhatsApp",
};

function DisponibleBadge({ disponible }: { disponible: boolean | null }) {
  if (disponible === true) return <span className="badge badge--ok">Disponible</span>;
  if (disponible === false) return <span className="badge badge--off">No disponible</span>;
  return <span className="badge">Sin datos</span>;
}

export function Integraciones() {
  const { me } = useAuth();
  const toast = useToast();
  const esAdmin = me?.roles.includes("ADMIN") ?? false;

  const [items, setItems] = useState<IntegracionEstado[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [accion, setAccion] = useState<string | null>(null); // proveedor con acción en curso

  const cargar = useCallback((signal?: AbortSignal) => {
    return getIntegracionesEstado(signal)
      .then((r) => setItems(r.integraciones))
      .catch((e: unknown) => {
        if (!signal?.aborted) {
          setError(e instanceof Error ? e.message : "No se pudo cargar el estado de las integraciones.");
        }
      });
  }, []);

  useEffect(() => {
    if (!esAdmin) return;
    const controller = new AbortController();
    cargar(controller.signal);
    return () => controller.abort();
  }, [esAdmin, cargar]);

  if (!esAdmin) return <Navigate to="/dashboard" replace />;

  async function onResyncCupones() {
    setAccion("tiendanube");
    try {
      const r = await resyncCupones();
      toast.success(
        `Cupones: ${r.sincronizados}/${r.intentados} sincronizados · ${r.pendientes} pendientes.`,
      );
      await cargar();
    } catch (e) {
      toast.error(e instanceof ApiRequestError ? e.message : "No se pudo reintentar los cupones.");
    } finally {
      setAccion(null);
    }
  }

  async function onSyncProductos() {
    setAccion("contabilium");
    try {
      const r = await syncProductos();
      toast.success(
        `Catálogo: ${r.creados} nuevos · ${r.actualizados} actualizados · ${r.sinCambios} sin cambios.`,
      );
      await cargar();
    } catch (e) {
      // En stub esto devuelve 503 con el mensaje explícito por proveedor; lo mostramos tal cual.
      toast.error(e instanceof ApiRequestError ? e.message : "No se pudo sincronizar el catálogo.");
    } finally {
      setAccion(null);
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Integraciones</h1>
          <p className="muted">Estado de los servicios externos y acciones de recuperación.</p>
        </div>
        <button className="btn btn--ghost btn--sm" onClick={() => cargar()} disabled={accion !== null}>
          Actualizar
        </button>
      </div>

      {error && <div className="alert alert--error">{error}</div>}
      {items === null && !error && <p className="muted">Cargando…</p>}

      {items && (
        <div className="integraciones-grid">
          {items.map((it) => (
            <article key={it.proveedor} className="card">
              <div className="integracion__head">
                <span className="integracion__name">{NOMBRE[it.proveedor] ?? it.proveedor}</span>
                <div className="integracion__badges">
                  <span className={"badge" + (it.modo === "live" ? " badge--ok" : "")}>{it.modo}</span>
                  <DisponibleBadge disponible={it.disponible} />
                  {it.pendientes > 0 && (
                    <span className="badge badge--wait">{it.pendientes} pendiente{it.pendientes === 1 ? "" : "s"}</span>
                  )}
                </div>
              </div>

              <div className="integracion__meta">
                <div className="integracion__meta-row">
                  <span className="muted">Última sincronización</span>
                  <span>{it.ultimaSync ? fechaHora(it.ultimaSync) : "—"}</span>
                </div>
                {it.ultimoError && (
                  <div className="integracion__meta-row">
                    <span className="muted">Último error</span>
                    <span title={it.ultimoErrorAt ? fechaHora(it.ultimoErrorAt) : undefined}>
                      {it.ultimoError}
                    </span>
                  </div>
                )}
              </div>

              {it.proveedor === "tiendanube" && (
                <button
                  className="btn btn--primary btn--sm integracion__action"
                  onClick={onResyncCupones}
                  disabled={accion !== null}
                >
                  {accion === "tiendanube" ? "Reintentando…" : "Reintentar cupones"}
                </button>
              )}
              {it.proveedor === "contabilium" && (
                <button
                  className="btn btn--primary btn--sm integracion__action"
                  onClick={onSyncProductos}
                  disabled={accion !== null}
                >
                  {accion === "contabilium" ? "Sincronizando…" : "Sincronizar catálogo"}
                </button>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
