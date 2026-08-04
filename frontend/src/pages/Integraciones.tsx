// Panel de integraciones (solo ADMIN): estado de los servicios externos + acciones manuales de
// recuperación (resiliencia 2.7–2.9). GET /admin/integraciones/estado + POST resync-cupones /
// sync-productos. En stub todo degrada con mensaje explícito (el 503 de Contabilium se surfacea tal cual).

import { useCallback, useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { ApiRequestError } from "../api/client";
import { getIntegracionesEstado, resyncCupones, syncProductos } from "../api/integraciones";
import { useAuth } from "../auth/AuthContext";
import { MaestroImportCard } from "../components/admin/MaestroImportCard";
import { useToast } from "../components/ui/Toast";
import { fechaHora } from "../lib/format";
import type { IntegracionEstado } from "../types/integraciones";

const NOMBRE: Record<string, string> = {
  contabilium: "Contabilium · ERP",
  tiendanube: "TiendaNube · tienda",
  mail: "Email",
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
      const r = await syncProductos(); // 202: la sync corre en background
      toast.success(r.mensaje);
    } catch (e) {
      // En stub esto degrada; el mensaje explícito por proveedor se surfacea tal cual.
      toast.error(e instanceof ApiRequestError ? e.message : "No se pudo iniciar la sincronización.");
      setAccion(null);
      return;
    }
    // Seguimos el progreso: refrescamos el estado cada 3s hasta que `sincronizando` pase a false.
    await cargar();
    for (let i = 0; i < 80; i++) {
      await new Promise((res) => setTimeout(res, 3000));
      try {
        const est = await getIntegracionesEstado();
        setItems(est.integraciones);
        const c = est.integraciones.find((x) => x.proveedor === "contabilium");
        if (!c?.sincronizando) {
          const res = c?.ultimoResultado ?? "";
          if (res.startsWith("error")) toast.error("Sincronización finalizada con error: " + res.slice(7).trim());
          else toast.success("Catálogo sincronizado. " + res);
          break;
        }
      } catch {
        // error transitorio al pollear: seguimos intentando
      }
    }
    setAccion(null);
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
                {it.proveedor === "contabilium" && it.ultimoResultado && (
                  <div className="integracion__meta-row">
                    <span className="muted">Último sync</span>
                    <span>{it.ultimoResultado}</span>
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
                  disabled={accion !== null || it.sincronizando === true}
                >
                  {accion === "contabilium" || it.sincronizando ? "Sincronizando…" : "Sincronizar catálogo"}
                </button>
              )}
            </article>
          ))}
          {/* No es una "integración" con estado propio como las otras cuatro (no hay conexión que
              monitorear, es un archivo que sube el admin), pero va acá porque es el mismo trabajo:
              refrescar el catálogo, y se hace justo después de sincronizar Contabilium. */}
          <MaestroImportCard disabled={accion !== null} />
        </div>
      )}
    </section>
  );
}
