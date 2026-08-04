// C-06 — cierre consolidado del admin: cuánto comisionó cada nutricionista en un rango, con
// exportable y liquidación de un click (call 55:28 y 57:02). Rango configurable, no mes calendario.

import { useCallback, useState } from "react";
import { ApiRequestError } from "../api/client";
import { getCierreConsolidado, liquidarRecetas } from "../api/cierres";
import { EmptyState } from "../components/ui/EmptyState";
import { Icon } from "../components/ui/Icon";
import { TableSkeleton } from "../components/ui/Skeleton";
import { useDialog } from "../components/ui/Dialog";
import { useToast } from "../components/ui/Toast";
import { useFetch } from "../hooks/useFetch";
import { descargarCsv, generarCsv } from "../lib/csv";
import { money } from "../lib/format";
import type { CierreFila } from "../types/cierre";

/** Primer y último día del mes en curso — el corte habitual, editable. */
function mesActual(): { desde: string; hasta: string } {
  const hoy = new Date();
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return {
    desde: iso(new Date(hoy.getFullYear(), hoy.getMonth(), 1)),
    hasta: iso(new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0)),
  };
}

export function CierreConsolidado() {
  const inicial = mesActual();
  const [desde, setDesde] = useState(inicial.desde);
  const [hasta, setHasta] = useState(inicial.hasta);
  const [liquidando, setLiquidando] = useState<string | null>(null);
  const toast = useToast();
  const { confirmar } = useDialog();

  const fetcher = useCallback(
    (signal: AbortSignal) => getCierreConsolidado(desde, hasta, signal),
    [desde, hasta],
  );
  const { data, loading, error, refetch } = useFetch(fetcher, [desde, hasta]);

  function exportar() {
    if (!data || data.filas.length === 0) return;
    const csv = generarCsv(
      ["Nutricionista", "CUIT", "Email", "Recetas", "Facturado", "Comisión", "Comisión pendiente"],
      data.filas.map((f) => [
        `${f.nombre ?? ""} ${f.apellido ?? ""}`.trim(),
        f.cuit ?? "",
        f.email,
        f.recetas,
        f.facturado,
        f.comision,
        f.comisionPendiente,
      ]),
    );
    descargarCsv(`cierre-${desde}_${hasta}.csv`, csv);
  }

  async function liquidar(f: CierreFila) {
    const cuantas = f.recetasPendientes;
    const ok = await confirmar({
      titulo: `Liquidar ${cuantas} receta${cuantas === 1 ? "" : "s"} de ${f.nombre} ${f.apellido}`,
      mensaje: (
        <>
          <p>
            Vas a registrar el pago de <strong>{money(f.comisionPendiente)}</strong> de comisión.
          </p>
          <p>
            Esas recetas dejan de figurar como pendientes de liquidar, pero siguen contando en el
            histórico.
          </p>
        </>
      ),
      confirmar: "Marcar como liquidadas",
    });
    if (!ok) return;
    setLiquidando(f.nutricionistaId);
    try {
      const r = await liquidarRecetas(f.recetaIdsPendientes);
      toast.success(`${r.liquidadas} receta(s) liquidadas por ${money(r.comisionTotal)}.`);
      if (r.omitidas.length > 0) {
        toast.error(`${r.omitidas.length} quedaron afuera: ${r.omitidas[0].motivo}.`);
      }
      refetch();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "No se pudo liquidar.");
    } finally {
      setLiquidando(null);
    }
  }

  const hayFilas = (data?.filas.length ?? 0) > 0;

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Cierre de comisiones</h1>
          <p className="muted">
            Lo que cada nutricionista generó en el período, según lo realmente pagado en la tienda.
          </p>
        </div>
        <button className="btn btn--ghost" onClick={exportar} disabled={!hayFilas}>
          <Icon name="download" size={17} />
          Exportar CSV
        </button>
      </div>

      <div className="filtros card">
        <label className="filtros__campo">
          <span>Desde</span>
          <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label className="filtros__campo">
          <span>Hasta</span>
          <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
        </label>
      </div>

      {loading && <TableSkeleton rows={4} cols={6} />}
      {error && <div className="alert alert--error">{error}</div>}

      {data && !hayFilas && !loading && (
        <EmptyState
          icon="trending-up"
          title="Sin conversiones en este período"
          hint="Probá con otro rango de fechas."
        />
      )}

      {data && hayFilas && (
        <>
          <div className="tiles">
            <div className="card tile">
              <span className="tile__body">
                <span className="tile__label">Recetas convertidas</span>
                <span className="tile__value">{data.totales.recetas}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__body">
                <span className="tile__label">Facturado</span>
                <span className="tile__value">{money(data.totales.facturado)}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__body">
                <span className="tile__label">Comisión total</span>
                <span className="tile__value">{money(data.totales.comision)}</span>
              </span>
            </div>
            <div className="card tile">
              <span className="tile__body">
                <span className="tile__label">A pagar (pendiente)</span>
                <span className="tile__value">{money(data.totales.comisionPendiente)}</span>
              </span>
            </div>
          </div>

          <table className="table">
            <thead>
              <tr>
                <th>Nutricionista</th>
                <th className="ta-right">Recetas</th>
                <th className="ta-right">Facturado</th>
                <th className="ta-right">Comisión</th>
                <th className="ta-right">Pendiente</th>
                <th className="table__actions">Acción</th>
              </tr>
            </thead>
            <tbody>
              {data.filas.map((f) => (
                <tr key={f.nutricionistaId}>
                  <td>
                    {f.nombre} {f.apellido}
                    <br />
                    <span className="muted">{f.email}</span>
                  </td>
                  <td className="ta-right">{f.recetas}</td>
                  <td className="ta-right">{money(f.facturado)}</td>
                  <td className="ta-right">{money(f.comision)}</td>
                  <td className="ta-right">
                    {f.recetasPendientes > 0 ? (
                      <strong>{money(f.comisionPendiente)}</strong>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td className="table__actions">
                    {f.recetasPendientes > 0 ? (
                      <button
                        className="btn btn--sm btn--primary"
                        disabled={liquidando === f.nutricionistaId}
                        onClick={() => liquidar(f)}
                      >
                        {liquidando === f.nutricionistaId
                          ? "Liquidando…"
                          : `Liquidar ${f.recetasPendientes}`}
                      </button>
                    ) : (
                      <span className="muted">Todo liquidado</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <p className="muted" style={{ fontSize: "0.8rem", marginTop: "0.9rem" }}>
            Los importes salen de lo que cada paciente pagó realmente en TiendaNube, no del precio
            de lista. El período se corta por fecha de compra, no por fecha de emisión de la receta.
          </p>
        </>
      )}
    </section>
  );
}
