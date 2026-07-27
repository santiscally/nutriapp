// Dashboard — primera pantalla post-login. Tiles de GET /dashboard/resumen + tabla de últimas recetas
// (clickeable → detalle, reusa el modal de F.5). El cierre mensual queda pendiente (endpoint en 500).

import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { EstadisticasCharts } from "../components/dashboard/EstadisticasCharts";
import { RecetaDetalle } from "../components/receta/RecetaDetalle";
import { EstadoBadge } from "../components/ui/EstadoBadge";
import { Icon } from "../components/ui/Icon";
import { TableSkeleton, TilesSkeleton } from "../components/ui/Skeleton";
import { useFetch } from "../hooks/useFetch";
import { fecha, money } from "../lib/format";
import type { DashboardResumen, Estadisticas } from "../types/dashboard";
import type { RecetaResponse } from "../types/receta";

const totalReceta = (r: RecetaResponse) => {
  const subtotal = r.items.reduce((acc, i) => acc + i.precioLista * i.cantidad, 0);
  return subtotal * (1 - r.descuentoPct / 100);
};

export function Dashboard() {
  const { me } = useAuth();
  const fetcher = useCallback(
    (signal: AbortSignal) => api.get<DashboardResumen>("/dashboard/resumen", undefined, signal),
    [],
  );
  const { data, loading, error } = useFetch(fetcher);
  const statsFetcher = useCallback(
    (signal: AbortSignal) =>
      api.get<Estadisticas>("/dashboard/estadisticas", { meses: 6 }, signal),
    [],
  );
  const { data: stats } = useFetch(statsFetcher);
  const [detalleId, setDetalleId] = useState<string | null>(null);

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Hola, {me?.nombre} 👋</h1>
          <p className="muted">
            {data
              ? `Tenés ${data.recetasPendientes} receta${data.recetasPendientes === 1 ? "" : "s"} pendiente${data.recetasPendientes === 1 ? "" : "s"}.`
              : "Tu resumen de hoy."}
          </p>
        </div>
        <div style={{ display: "flex", gap: "0.6rem", flexWrap: "wrap" }}>
          <Link className="btn btn--ghost" to="/pacientes">
            <Icon name="users" size={17} />
            Nuevo paciente
          </Link>
          <Link className="btn btn--ghost" to="/cierre-mensual">
            <Icon name="trending-up" size={17} />
            Ver cierre
          </Link>
        </div>
      </div>

      {loading && (
        <>
          <TilesSkeleton />
          <div className="section-head">
            <h2 className="section-title">Últimas recetas</h2>
          </div>
          <TableSkeleton rows={5} cols={6} />
        </>
      )}
      {error && <div className="alert alert--error">{error}</div>}

      {data && (
        <>
          <div className="tiles">
            <div className="card tile">
              <span className="tile__icon tile__icon--amber">
                <Icon name="clock" />
              </span>
              <span className="tile__body">
                <span className="tile__label">Recetas pendientes</span>
                <span className="tile__value">{data.recetasPendientes}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__icon tile__icon--green">
                <Icon name="check-circle" />
              </span>
              <span className="tile__body">
                <span className="tile__label">Aplicadas (mes)</span>
                <span className="tile__value">{data.recetasAplicadasMes}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__icon tile__icon--blue">
                <Icon name="trending-up" />
              </span>
              <span className="tile__body">
                <span className="tile__label">Comisión (mes)</span>
                <span className="tile__value">{money(data.comisionMesActual)}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__icon tile__icon--green">
                <Icon name="trending-up" />
              </span>
              <span className="tile__body">
                <span className="tile__label">Ventas generadas (mes)</span>
                <span className="tile__value">{money(data.ventasGeneradasMesActual)}</span>
              </span>
            </div>
          </div>

          {stats && <EstadisticasCharts stats={stats} />}

          <div className="section-head">
            <h2 className="section-title">Últimas recetas</h2>
            <Link className="section-head__link" to="/recetas">
              Ver todas →
            </Link>
          </div>

          {data.ultimasRecetas.length === 0 ? (
            <p className="muted">Todavía no emitiste recetas.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Paciente</th>
                  <th>Estado</th>
                  <th>Emitida</th>
                  <th>Vence</th>
                  <th className="ta-right">Total</th>
                </tr>
              </thead>
              <tbody>
                {data.ultimasRecetas.map((r) => (
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
                    <td className="ta-right">{money(totalReceta(r))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}

      {detalleId && (
        <RecetaDetalle id={detalleId} onClose={() => setDetalleId(null)} onChanged={() => {}} />
      )}
    </section>
  );
}
