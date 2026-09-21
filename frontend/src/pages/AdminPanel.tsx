// F-24 — solapa PANEL del admin: el mismo panel que ve la profesional, pero consolidado sobre
// todas. Fuente: GET /admin/dashboard/resumen + /admin/dashboard/estadisticas (S-13).
//
// La diferencia con el panel de ella no es cosmética: acá SÍ se muestra la facturación, porque es
// con lo que el admin liquida (misma línea que el cierre consolidado, C-06). En los endpoints de
// la profesional ese número directamente no viaja.

import { useCallback } from "react";
import { Link } from "react-router-dom";
import { getAdminEstadisticas, getAdminResumen } from "../api/admin";
import { EstadisticasCharts } from "../components/dashboard/EstadisticasCharts";
import { EstadoBadge } from "../components/ui/EstadoBadge";
import { Icon } from "../components/ui/Icon";
import { TableSkeleton, TilesSkeleton } from "../components/ui/Skeleton";
import { useFetch } from "../hooks/useFetch";
import { fecha, money } from "../lib/format";

export function AdminPanel() {
  const { data, loading, error } = useFetch(
    useCallback((s: AbortSignal) => getAdminResumen(s), []),
  );
  const { data: stats } = useFetch(
    useCallback((s: AbortSignal) => getAdminEstadisticas(6, s), []),
  );

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Panel</h1>
          <p className="muted">
            {data
              ? `${data.profesionalesActivos} profesional${data.profesionalesActivos === 1 ? "" : "es"} activo${data.profesionalesActivos === 1 ? "" : "s"}` +
                (data.profesionalesPendientes > 0
                  ? ` · ${data.profesionalesPendientes} solicitud${data.profesionalesPendientes === 1 ? "" : "es"} sin responder`
                  : "")
              : "Resumen de toda la operación."}
          </p>
        </div>
        <div style={{ display: "flex", gap: "0.6rem", flexWrap: "wrap" }}>
          <Link className="btn btn--ghost" to="/profesionales">
            <Icon name="users" size={17} />
            Profesionales
          </Link>
          <Link className="btn btn--ghost" to="/cierres">
            <Icon name="trending-up" size={17} />
            Cierre consolidado
          </Link>
        </div>
      </div>

      {loading && (
        <>
          <TilesSkeleton />
          <div className="section-head">
            <h2 className="section-title">Últimos bonos</h2>
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
                <span className="tile__label">Bonos pendientes</span>
                <span className="tile__value">{data.recetasPendientes}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__icon tile__icon--green">
                <Icon name="check-circle" />
              </span>
              <span className="tile__body">
                <span className="tile__label">Aplicados (mes)</span>
                <span className="tile__value">{data.recetasAplicadasMes}</span>
                {/* Los vencidos del mes van de subtexto y no de tile propio: es el contrapunto
                    del número de arriba, no una métrica que se mire sola. */}
                <span className="muted">{data.recetasVencidasMes} vencidos</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__icon tile__icon--blue">
                <Icon name="trending-up" />
              </span>
              <span className="tile__body">
                <span className="tile__label">Comisiones del mes</span>
                <span className="tile__value">{money(data.comisionMesActual)}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__icon tile__icon--blue">
                <Icon name="trending-up" />
              </span>
              <span className="tile__body">
                <span className="tile__label">Facturado (mes)</span>
                <span className="tile__value">{money(data.facturadoMesActual)}</span>
              </span>
            </div>
          </div>

          {stats && <EstadisticasCharts stats={stats} />}

          <div className="section-head">
            <h2 className="section-title">Últimos bonos</h2>
            <Link className="section-head__link" to="/admin/bonos">
              Ver todos →
            </Link>
          </div>

          {data.ultimasRecetas.length === 0 ? (
            <p className="muted">Todavía no se emitió ningún bono.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Profesional</th>
                  <th>Paciente</th>
                  <th>Estado</th>
                  <th>Emitido</th>
                  <th className="ta-right">Comisión</th>
                </tr>
              </thead>
              <tbody>
                {data.ultimasRecetas.map((r) => (
                  <tr key={r.id}>
                    <td className="mono">{r.codigo}</td>
                    <td>
                      {r.nutricionista.nombre} {r.nutricionista.apellido}
                    </td>
                    <td>
                      {r.paciente.nombre} {r.paciente.apellido}
                    </td>
                    <td>
                      <EstadoBadge estado={r.estado} />
                    </td>
                    <td className="muted">{fecha(r.emitidaAt)}</td>
                    <td className="ta-right">
                      {r.conversion ? (
                        money(r.conversion.comisionMonto)
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  );
}
