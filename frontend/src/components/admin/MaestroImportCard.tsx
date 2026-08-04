// C-12 — Carga del maestro de artículos de TBC desde el panel de integraciones (solo admin).
// Es lo que pidió Gon por mail: un "examinar", se inserta el Excel y avisa que se importó.
//
// El cartel de éxito muestra el `mensaje` que arma el backend, que trae los números adentro: un
// "importado correctamente" pelado sería engañoso si de 2225 filas matchearon 300. Si quedaron SKUs
// sin producto en el catálogo, se pueden desplegar para que Gon vea cuáles.

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiRequestError } from "../../api/client";
import { getMaestroEstado, importarMaestro } from "../../api/maestro";
import { fechaHora } from "../../lib/format";
import type { ImportarMaestroResponse, MaestroEstado } from "../../types/maestro";
import { useToast } from "../ui/Toast";

interface Props {
  /** Bloquea el botón mientras corre otra acción del panel (ej. la sync de Contabilium). */
  disabled?: boolean;
}

export function MaestroImportCard({ disabled = false }: Props) {
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [estado, setEstado] = useState<MaestroEstado | null>(null);
  const [reporte, setReporte] = useState<ImportarMaestroResponse | null>(null);
  const [verSinMatch, setVerSinMatch] = useState(false);

  const cargarEstado = useCallback((signal?: AbortSignal) => {
    getMaestroEstado(signal)
      .then(setEstado)
      .catch(() => {
        // El estado es informativo: si falla, la card sigue sirviendo para importar.
      });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    cargarEstado(controller.signal);
    return () => controller.abort();
  }, [cargarEstado]);

  async function onImportar() {
    if (!archivo) return;
    setSubiendo(true);
    setReporte(null);
    setVerSinMatch(false);
    try {
      const r = await importarMaestro(archivo);
      setReporte(r);
      // Sin match o filas rechazadas no es un fallo, pero tampoco un éxito limpio: lo avisamos distinto.
      if (r.filasSinMatch > 0 || r.filasRechazadas > 0) toast.error(r.mensaje);
      else toast.success(r.mensaje);
      setArchivo(null);
      if (inputRef.current) inputRef.current.value = "";
      cargarEstado();
    } catch (e) {
      toast.error(
        e instanceof ApiRequestError ? e.message : "No se pudo importar el maestro de artículos.",
      );
    } finally {
      setSubiendo(false);
    }
  }

  return (
    <article className="card">
      <div className="integracion__head">
        <span className="integracion__name">Maestro de artículos · Excel</span>
        <span className="badge badge--ok">manual</span>
      </div>

      <div className="integracion__meta">
        <div className="integracion__meta-row">
          <span className="muted">Última importación</span>
          <span>{estado?.importadoAt ? fechaHora(estado.importadoAt) : "—"}</span>
        </div>
        {estado?.filasLeidas != null && (
          <div className="integracion__meta-row">
            <span className="muted">Último resultado</span>
            <span>
              {estado.filasMatcheadas} de {estado.filasLeidas} filas
              {estado.filasSinMatch ? ` · ${estado.filasSinMatch} sin match` : ""}
            </span>
          </div>
        )}
        <p className="muted maestro__hint">
          Orden sugerido: <strong>1.</strong> sincronizar el catálogo de Contabilium →{" "}
          <strong>2.</strong> importar este Excel. Aporta departamento, categoría, subcategoría,
          laboratorio, tags e imagen; no toca precios ni stock.
        </p>
      </div>

      <div className="maestro__upload">
        <input
          ref={inputRef}
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
          disabled={disabled || subiendo}
          aria-label="Archivo del maestro de artículos"
        />
      </div>

      {reporte && (
        <div className={"maestro__reporte" + (reporte.filasSinMatch > 0 ? " maestro__reporte--warn" : "")}>
          <div className="integracion__meta-row">
            <span className="muted">Filas aplicadas</span>
            <span>
              {reporte.filasMatcheadas} de {reporte.filasLeidas}
            </span>
          </div>
          {reporte.despublicados > 0 && (
            <div className="integracion__meta-row">
              <span className="muted">Dejaron de estar disponibles</span>
              <span>{reporte.despublicados}</span>
            </div>
          )}
          {reporte.publicados > 0 && (
            <div className="integracion__meta-row">
              <span className="muted">Volvieron a estar disponibles</span>
              <span>{reporte.publicados}</span>
            </div>
          )}
          {reporte.filasSinMatch > 0 && (
            <>
              <button
                className="btn btn--sm btn--ghost maestro__toggle"
                onClick={() => setVerSinMatch((v) => !v)}
              >
                {verSinMatch ? "Ocultar" : "Ver"} los {reporte.filasSinMatch} SKU sin producto en el
                catálogo
              </button>
              {verSinMatch && (
                <p className="mono maestro__skus">
                  {reporte.skusSinMatch.join(", ")}
                  {reporte.filasSinMatch > reporte.skusSinMatch.length && " …"}
                </p>
              )}
            </>
          )}
          {reporte.rechazos.length > 0 && (
            <ul className="maestro__rechazos">
              {reporte.rechazos.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <button
        className="btn btn--primary btn--sm integracion__action"
        onClick={onImportar}
        disabled={disabled || subiendo || !archivo}
      >
        {subiendo ? "Importando…" : "Importar maestro"}
      </button>
    </article>
  );
}
