// C-09 — ficha de una nutricionista: datos del registro, sus % (obligatorios desde V011: se
// eliminó el valor global) y las acciones del admin sobre su cuenta.
//
// Las cuatro acciones de cuenta son distintas y no intercambiables:
//   · Aprobar/Rechazar → resuelven la SOLICITUD, sólo mientras está pendiente.
//   · Desactivar/Reactivar → cortan o devuelven el acceso conservando todo. Para bajas.
//   · Borrar → elimina de verdad. Para altas equivocadas; el backend lo frena si emitió recetas.
//   · Nueva contraseña → única vía de recuperación que existe (no hay "olvidé mi contraseña").

import { useState, type FormEvent } from "react";
import { ApiRequestError } from "../../api/client";
import {
  abrirMatricula,
  actualizarParametros,
  aprobarNutricionista,
  desactivarNutricionista,
  eliminarNutricionista,
  reactivarNutricionista,
  rechazarNutricionista,
  resetearPassword,
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
  const n = Number(v);
  return v.trim() !== "" && !Number.isNaN(n) && n >= 0 && n <= 100;
};

export function ParametrosModal({ nutri, onClose, onChanged }: Props) {
  const toast = useToast();
  const [descuento, setDescuento] = useState(nutri.descuentoPct.toString());
  const [comision, setComision] = useState(nutri.comisionPct.toString());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pendiente = nutri.estadoValidacion === "PENDIENTE";
  const aprobada = nutri.estadoValidacion === "APROBADA";

  async function guardarParametros(): Promise<boolean> {
    if (!pctValido(descuento) || !pctValido(comision)) {
      setError("Los dos porcentajes son obligatorios y van entre 0 y 100.");
      return false;
    }
    await actualizarParametros(nutri.id, {
      descuentoPct: Number(descuento),
      comisionPct: Number(comision),
    });
    return true;
  }

  async function correr(accion: () => Promise<unknown>, ok: string, cerrar = true) {
    setBusy(true);
    setError(null);
    try {
      await accion();
      toast.success(ok);
      onChanged();
      if (cerrar) onClose();
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

  function onDesactivar() {
    if (!window.confirm(`${nutri.nombre} no va a poder entrar más, pero se conservan sus recetas y pacientes. ¿Seguimos?`)) return;
    correr(() => desactivarNutricionista(nutri.id), "Acceso desactivado.");
  }

  function onReactivar() {
    correr(() => reactivarNutricionista(nutri.id), "Acceso reactivado.");
  }

  function onEliminar() {
    // Doble confirmación con el nombre a la vista: es irreversible y no hay papelera.
    if (!window.confirm(
      `Se va a BORRAR a ${nutri.nombre} ${nutri.apellido} y sus pacientes, sin vuelta atrás.\n\n` +
        "Si sólo querés que no pueda entrar, cancelá y usá \"Desactivar\".",
    )) return;
    correr(() => eliminarNutricionista(nutri.id), "Nutricionista eliminada.");
  }

  function onResetPassword() {
    const nueva = window.prompt(
      `Contraseña nueva para ${nutri.email} (mínimo 8 caracteres).\nAvisale por un canal seguro.`,
    );
    if (nueva === null) return;
    if (nueva.trim().length < 8) {
      setError("La contraseña debe tener al menos 8 caracteres.");
      return;
    }
    correr(() => resetearPassword(nutri.id, nueva), "Contraseña actualizada.", false);
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
            <dt>Acceso</dt>
            <dd>
              {nutri.activo ? (
                <span className="badge badge--ok">Activo</span>
              ) : (
                <span className="badge badge--off">Sin acceso</span>
              )}
            </dd>
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
              required
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
              required
              value={comision}
              onChange={(e) => setComision(e.target.value)}
            />
          </label>
        </div>
        <p className="muted" style={{ marginTop: "-0.4rem", fontSize: "0.8rem" }}>
          Los cambios afectan sólo a las recetas futuras: las ya emitidas conservan el porcentaje
          con el que salieron.
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

        {/* Acciones sobre la cuenta, separadas de los datos: no se tocan en la operación diaria. */}
        {!pendiente && (
          <>
            <h3 className="detalle__title">Cuenta</h3>
            <div className="detalle__actions">
              <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={onResetPassword}>
                Nueva contraseña
              </button>
              {aprobada && nutri.activo && (
                <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={onDesactivar}>
                  Desactivar
                </button>
              )}
              {aprobada && !nutri.activo && (
                <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={onReactivar}>
                  Reactivar
                </button>
              )}
              <button type="button" className="btn btn--sm btn--danger" disabled={busy} onClick={onEliminar}>
                Borrar
              </button>
            </div>
            <p className="muted" style={{ fontSize: "0.8rem" }}>
              <strong>Desactivar</strong> le saca el acceso y se puede revertir.{" "}
              <strong>Borrar</strong> la elimina para siempre y sólo funciona si nunca emitió una
              receta — las que ya emitió forman parte de los cierres.
            </p>
          </>
        )}
      </form>
    </Modal>
  );
}
