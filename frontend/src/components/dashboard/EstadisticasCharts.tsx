// Gráficos del dashboard a partir de GET /dashboard/estadisticas (serie mensual real):
// barras de recetas emitidas por mes + tarjeta de comisión del mes con tendencia y ticket promedio.
// Todo derivado de datos reales del backend — nada hardcodeado.

import { money } from "../../lib/format";
import type { Estadisticas } from "../../types/dashboard";

const MESES_CORTOS = [
  "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic",
];

export function EstadisticasCharts({ stats }: { stats: Estadisticas }) {
  const meses = stats.meses;
  if (meses.length === 0) return null;

  const maxEmitidas = Math.max(1, ...meses.map((m) => m.recetasEmitidas));
  const actual = meses[meses.length - 1];
  const previo = meses.length > 1 ? meses[meses.length - 2] : null;
  const deltaPct =
    previo && previo.comisionTotal > 0
      ? Math.round(((actual.comisionTotal - previo.comisionTotal) / previo.comisionTotal) * 100)
      : null;

  return (
    <div className="charts">
      <div className="card chart-card">
        <div className="chart-card__head">
          <h2 className="chart-card__title">Bonos por mes</h2>
          <span className="muted">Últimos {meses.length} meses</span>
        </div>
        <div className="bars">
          {meses.map((m, i) => {
            const isNow = i === meses.length - 1;
            const h = Math.round((m.recetasEmitidas / maxEmitidas) * 100);
            return (
              <div className="bars__col" key={`${m.year}-${m.month}`}>
                <span className={"bars__val" + (isNow ? " bars__val--now" : "")}>
                  {m.recetasEmitidas}
                </span>
                <div
                  className={"bars__bar" + (isNow ? " bars__bar--now" : "")}
                  style={{ height: `${h}%` }}
                />
                <span className={"bars__label" + (isNow ? " bars__label--now" : "")}>
                  {MESES_CORTOS[m.month - 1]}
                </span>
              </div>
            );
          })}
        </div>
        <div className="bars__legend">
          <span className="bars__legend-item">
            <span className="bars__dot bars__dot--now" />
            Mes en curso
          </span>
          <span className="bars__legend-item">
            <span className="bars__dot" />
            Meses cerrados
          </span>
        </div>
      </div>

      <div className="card chart-card">
        <div className="chart-card__head">
          <h2 className="chart-card__title">Comisión de {MESES_CORTOS[actual.month - 1]}</h2>
        </div>
        <div className="chart-card__big">
          <span className="muted">Devengado</span>
          <span className="chart-card__amount">{money(actual.comisionTotal)}</span>
          {deltaPct !== null && (
            <span
              className={"chart-card__delta" + (deltaPct >= 0 ? "" : " chart-card__delta--down")}
            >
              {deltaPct >= 0 ? "+" : ""}
              {deltaPct}% vs mes anterior
            </span>
          )}
        </div>
        <dl className="chart-card__stats">
          <div>
            <dt>Bonos aplicados</dt>
            <dd>{actual.recetasAplicadas}</dd>
          </div>
          <div>
            <dt>Comisión del mes</dt>
            <dd>{money(actual.comisionTotal)}</dd>
          </div>
        </dl>
      </div>
    </div>
  );
}
