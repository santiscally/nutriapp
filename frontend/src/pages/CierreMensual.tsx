// Cierre mensual — comisiones por recetas aplicadas. GET /dashboard/cierre-mensual?year=&month=.
// Solo data real del backend; el mes se elige de los últimos 12. La conversión se calcula localmente.

import { useCallback, useMemo, useState } from "react";
import { api } from "../api/client";
import { TableSkeleton, TilesSkeleton } from "../components/ui/Skeleton";
import { useFetch } from "../hooks/useFetch";
import { fecha, money } from "../lib/format";
import type { CierreMensual as Cierre } from "../types/dashboard";

interface Mes {
  year: number;
  month: number;
  label: string;
}

function ultimosMeses(n: number): Mes[] {
  const now = new Date();
  const out: Mes[] = [];
  for (let i = 0; i < n; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    out.push({
      year: d.getFullYear(),
      month: d.getMonth() + 1,
      label: d.toLocaleDateString("es-AR", { month: "long", year: "numeric" }),
    });
  }
  return out;
}

export function CierreMensual() {
  const meses = useMemo(() => ultimosMeses(12), []);
  const [sel, setSel] = useState(() => `${meses[0].year}-${meses[0].month}`);
  const [year, month] = sel.split("-").map(Number);

  const fetcher = useCallback(
    (signal: AbortSignal) =>
      api.get<Cierre>("/dashboard/cierre-mensual", { year, month }, signal),
    [year, month],
  );
  const { data, loading, error } = useFetch(fetcher, [year, month]);

  const now = new Date();
  const enCurso = year === now.getFullYear() && month === now.getMonth() + 1;
  const conversion =
    data && data.recetasEmitidas > 0
      ? Math.round((data.recetasAplicadas / data.recetasEmitidas) * 100)
      : 0;

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Cierre mensual</h1>
          <p className="muted">
            Comisiones por recetas aplicadas. Solo suman las recetas con compra confirmada en la
            tienda.
          </p>
        </div>
        <select value={sel} onChange={(e) => setSel(e.target.value)}>
          {meses.map((m) => (
            <option key={`${m.year}-${m.month}`} value={`${m.year}-${m.month}`}>
              {m.label}
              {m.year === now.getFullYear() && m.month === now.getMonth() + 1 ? " · en curso" : ""}
            </option>
          ))}
        </select>
      </div>

      {loading && (
        <>
          <TilesSkeleton />
          <div className="section-head">
            <h2 className="section-title">Recetas que suman al cierre</h2>
          </div>
          <TableSkeleton rows={5} cols={5} />
        </>
      )}
      {error && <div className="alert alert--error">{error}</div>}

      {data && !loading && (
        <>
          <div className="tiles">
            <div className="card">
              <div className="tile__label">Recetas aplicadas</div>
              <div className="tile__value">{data.recetasAplicadas}</div>
              <div className="muted">
                de {data.recetasEmitidas} emitidas · {conversion}% de conversión
              </div>
            </div>
            <div className="card">
              <div className="tile__label">Comisión total</div>
              <div className="tile__value">{money(data.comisionTotal)}</div>
            </div>
            <div
              className="card"
              style={{ background: "var(--ink)", borderColor: "var(--ink)", color: "#fff" }}
            >
              <div style={{ color: "#a9c2bc", fontSize: "0.85rem" }}>Estado del cierre</div>
              <div style={{ fontSize: "1.5rem", fontWeight: 700, marginTop: "0.4rem" }}>
                {enCurso ? "En curso" : "Cerrado"}
              </div>
              <div style={{ color: "#a9c2bc", fontSize: "0.8rem", marginTop: "0.3rem" }}>
                {enCurso ? "Se liquida al cierre del mes" : "Mes liquidado"}
              </div>
            </div>
          </div>

          <div className="section-head">
            <h2 className="section-title">Recetas que suman al cierre</h2>
          </div>

          {data.detalle.length === 0 ? (
            <p className="muted">No hay recetas aplicadas en este mes todavía.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Paciente</th>
                  <th className="ta-right">Comisión</th>
                  <th className="ta-right">Pagada</th>
                </tr>
              </thead>
              <tbody>
                {data.detalle.map((d) => (
                  <tr key={d.recetaCodigo}>
                    <td className="mono">{d.recetaCodigo}</td>
                    <td>{d.paciente}</td>
                    <td className="ta-right" style={{ fontWeight: 700 }}>
                      {money(d.comisionMonto)}
                    </td>
                    <td className="ta-right muted">{fecha(d.paidAt)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={2} className="muted">
                    {data.recetasAplicadas} recetas aplicadas
                  </td>
                  <td className="ta-right" style={{ fontWeight: 700 }}>
                    {money(data.comisionTotal)}
                  </td>
                  <td />
                </tr>
              </tfoot>
            </table>
          )}
        </>
      )}
    </section>
  );
}
