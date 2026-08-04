// C-09 — ficha de una nutricionista: datos del registro + % propios (C-01) + aprobar/rechazar.
// Vacío en un % = "usá el global": así el admin no tiene que copiar el valor global en cada fila.

import { useState, type FormEvent } from "react";
import { ApiRequestError } from "../../api/client";
import {
  abrirMatricula,
  actualizarParametros,
  aprobarNutricionista,
  rechazarNutricionista,
} from "../../api/nutricionistas";
import { fecha } from "../../lib/format";
import type { NutricionistaAdmin } from "../../types/nutricionista";
import { Modal } from "../ui/Modal";
import { useToast } from "../ui/Toast";

interface Props {
  nutri: NutricionistaAdmin;
  onClose: () => void;
  onChanged: () => void;
}

const pctValido = (v: string) => {
  if (v.trim() === "") return true; // vacío = global
  const n = Number(v);
  return !Number.isNaN(n) && n >= 0 && n <= 100;
};

/** "" → null (global); "0" → 0, que es un override válido y NO debe caer al global. */
const aNumeroOnull = (v: string) => (v.trim() === "" ? null : Number(v));

export function ParametrosModal({ nutri, onClose, onChanged }: Props) {
  const toast = useToast();
  const [descuento, setDescuento] = useState(nutri.descuentoPct?.toString() ?? "");
  const [comision, setComision] = useState(nutri.comisionPct?.toString() ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pendiente = nutri.estadoValidacion === "PENDIENTE";

  async function guardarParametros(): Promise<boolean> {
    if (!pctValido(descuento) || !pctValido(comision)) {
      setError("Los porcentajes deben estar entre 0 y 100. Dejalos vacíos para usar el global.");
      return false;
    }
    await actualizarParametros(nutri.id, {
      descuentoPct: aNumeroOnull(descuento),
      comisionPct: aNumeroOnull(comision),
    });
    return true;
  }

  async function correr(accion: () => Promise<unknown>, ok: string) {
    setBusy(true);
    setError(null);
    try {
      await accion();
      toast.success(ok);
      onChanged();
      onClose();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "No se pudo completar la acción.");
    } finally {
      setBusy(false);
    }
  }

  async function onGuardar(e: FormEvent) {
    e.preventDefault();
    // Guardar los % es lo mismo apruebe o no: si está pendiente, se guardan y además se aprueba.
    await correr(async () => {
      if (!(await guardarParametros())) throw new Error("validación");
      if (pendiente) await aprobarNutricionista(nutri.id);
    }, pendiente ? "Nutricionista aprobada." : "Parámetros actualizados.");
  }

  function onRechazar() {
    const motivo = window.prompt("Motivo del rechazo (opcional):") ?? undefined;
    if (motivo === undefined && !window.confirm("¿Rechazar la solicitud sin motivo?")) return;
    correr(() => rechazarNutricionista(nutri.id, motivo), "Solicitud rechazada.");
  }

  return (
    <Modal title={`${nutri.nombre} ${nutri.apellido}`} onClose={onClose}>
      <form className="detalle" onSubmit={onGuardar}>
        <dl className="detalle__meta">
          <div>
            <dt>Email</dt>
            <dd>{nutri.email}</dd>
          </div>
          <div>
            <dt>Teléfono</dt>
            <dd>{nutri.telefono || <span className="muted">—</span>}</dd>
          </div>
          <div>
            <dt>Matrícula</dt>
            <dd>{nutri.matricula || <span className="muted">—</span>}</dd>
          </div>
          <div>
            <dt>DNI</dt>
            <dd>{nutri.dni || <span className="muted">—</span>}</dd>
          </div>
          <div>
            <dt>CUIT</dt>
            <dd>{nutri.cuit || <span className="muted">—</span>}</dd>
          </div>
          <div>
            <dt>Condición fiscal</dt>
            <dd>{nutri.condicionFiscal || <span className="muted">—</span>}</dd>
          </div>
          <div>
            <dt>Respaldo</dt>
            <dd>
              {nutri.tieneMatricula ? (
                <button
                  type="button"
                  className="btn btn--sm btn--ghost"
                  onClick={() =>
                    abrirMatricula(nutri.id).catch(() => toast.error("No se pudo abrir el archivo."))
                  }
                >
                  Ver matrícula
                </button>
              ) : (
                <span className="muted">No adjuntó archivo</span>
              )}
            </dd>
          </div>
          <div>
            <dt>Solicitud</dt>
            <dd>{fecha(nutri.createdAt)}</dd>
          </div>
        </dl>

        <h3 className="detalle__title">Porcentajes</h3>
        <div className="form-grid">
          <label className="field">
            <span>Descuento de recetas (%)</span>
            <input
              type="number"
              min={0}
              max={100}
              step="0.01"
              placeholder={`Global: ${nutri.descuentoPctEfectivo}`}
              value={descuento}
              onChange={(e) => setDescuento(e.target.value)}
            />
          </label>
          <label className="field">
            <span>Comisión (%)</span>
            <input
              type="number"
              min={0}
              max={100}
              step="0.01"
              placeholder={`Global: ${nutri.comisionPctEfectiva}`}
              value={comision}
              onChange={(e) => setComision(e.target.value)}
            />
          </label>
        </div>
        <p className="muted" style={{ marginTop: "-0.4rem", fontSize: "0.8rem" }}>
          Vacío = usa el valor global de Configuración. Los cambios afectan sólo a las recetas
          futuras: las ya emitidas conservan el porcentaje con el que salieron.
        </p>

        {nutri.estadoValidacion === "RECHAZADA" && nutri.notasValidacion && (
          <p className="muted">Motivo del rechazo: {nutri.notasValidacion}</p>
        )}

        {error && <div className="alert alert--error">{error}</div>}

        <div className="detalle__actions">
          {pendiente && (
            <button type="button" className="btn btn--sm btn--danger" disabled={busy} onClick={onRechazar}>
              Rechazar
            </button>
          )}
          <button type="submit" className="btn btn--sm btn--primary" disabled={busy}>
            {busy ? "Guardando…" : pendiente ? "Aprobar" : "Guardar"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
