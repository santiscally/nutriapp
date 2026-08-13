// C-09 — ficha de una nutricionista: datos del registro, sus % (obligatorios desde V011: se
// eliminó el valor global) y las acciones del admin sobre su cuenta.
//
// El modal está partido en dos zonas con jerarquías distintas, porque mezclarlas confunde:
//   · Arriba, la ficha y los porcentajes, con su botón de guardar (o aprobar, si está pendiente).
//     Es lo que se toca todos los días.
//   · Abajo, separada, la zona de cuenta: contraseña, desactivar/reactivar y borrar. Son acciones
//     que casi nunca se usan y dos de ellas son difíciles o imposibles de revertir.
//
// Las acciones no son intercambiables y por eso conviven:
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
import { useDialog } from "../ui/Dialog";
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
  const { confirmar, pedirTexto } = useDialog();
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

  async function onRechazar() {
    const motivo = await pedirTexto({
      titulo: "Rechazar la solicitud",
      mensaje: `${nutri.nombre} ${nutri.apellido} no va a poder entrar. Podés dejar registrado el motivo.`,
      etiqueta: "Motivo (opcional)",
      placeholder: "Ej.: la matrícula adjunta no se lee.",
      multilinea: true,
      opcional: true,
      confirmar: "Rechazar solicitud",
    });
    if (motivo === null) return;
    correr(() => rechazarNutricionista(nutri.id, motivo || undefined), "Solicitud rechazada.");
  }

  async function onDesactivar() {
    const ok = await confirmar({
      titulo: `Desactivar a ${nutri.nombre} ${nutri.apellido}`,
      mensaje: (
        <>
          <p>Deja de poder entrar, pero se conservan sus bonos, sus pacientes y sus datos.</p>
          <p>Podés devolverle el acceso cuando quieras.</p>
        </>
      ),
      confirmar: "Desactivar acceso",
    });
    if (ok) correr(() => desactivarNutricionista(nutri.id), "Acceso desactivado.");
  }

  function onReactivar() {
    correr(() => reactivarNutricionista(nutri.id), "Acceso reactivado.");
  }

  async function onEliminar() {
    const ok = await confirmar({
      titulo: `¿Borrar a ${nutri.nombre} ${nutri.apellido}?`,
      mensaje: (
        <>
          <p>
            Se eliminan <strong>su usuario, su perfil y sus pacientes</strong>. No se puede deshacer.
          </p>
          <p>
            Si sólo querés que no pueda entrar, cancelá y usá <strong>Desactivar</strong>: conserva
            todo y es reversible.
          </p>
        </>
      ),
      confirmar: "Borrar definitivamente",
      peligro: true,
    });
    if (ok) correr(() => eliminarNutricionista(nutri.id), "Nutricionista eliminada.");
  }

  async function onResetPassword() {
    const nueva = await pedirTexto({
      titulo: "Nueva contraseña",
      mensaje: (
        <p>
          Se la ponés vos a <strong>{nutri.email}</strong>. Pasásela por un canal seguro: no queda
          registrada en ningún lado.
        </p>
      ),
      etiqueta: "Contraseña nueva",
      tipo: "password",
      placeholder: "Mínimo 8 caracteres",
      validar: (v) => (v.length < 8 ? "La contraseña debe tener al menos 8 caracteres." : null),
      confirmar: "Cambiar contraseña",
    });
    if (nueva === null) return;
    correr(() => resetearPassword(nutri.id, nueva), "Contraseña actualizada.", false);
  }

  return (
    <Modal title={`${nutri.nombre} ${nutri.apellido}`} onClose={onClose} ancho>
      <form className="ficha" onSubmit={onGuardar}>
        {/* Estado arriba de todo: es lo primero que se pregunta el admin al abrir la ficha. */}
        <div className="ficha__estado">
          {nutri.activo ? (
            <span className="badge badge--ok">Acceso activo</span>
          ) : (
            <span className="badge badge--off">Sin acceso</span>
          )}
          <span className="muted">{nutri.email}</span>
          <span className="muted">·</span>
          <span className="muted">solicitó el {fecha(nutri.createdAt)}</span>
          {nutri.tieneMatricula && (
            <button
              type="button"
              className="btn btn--sm btn--ghost ficha__estado-btn"
              onClick={() =>
                abrirMatricula(nutri.id).catch(() => toast.error("No se pudo abrir el archivo."))
              }
            >
              Ver matrícula
            </button>
          )}
        </div>

        <dl className="ficha__datos">
          <div>
            <dt>Teléfono</dt>
            <dd>{nutri.telefono || "—"}</dd>
          </div>
          <div>
            <dt>Matrícula</dt>
            <dd>{nutri.matricula || "—"}</dd>
          </div>
          <div>
            <dt>DNI</dt>
            <dd>{nutri.dni || "—"}</dd>
          </div>
          <div>
            <dt>CUIT</dt>
            <dd>{nutri.cuit || "—"}</dd>
          </div>
          <div className="ficha__datos-ancho">
            <dt>Condición fiscal</dt>
            <dd>{nutri.condicionFiscal || "—"}</dd>
          </div>
          {!nutri.tieneMatricula && (
            <div className="ficha__datos-ancho">
              <dt>Respaldo</dt>
              <dd className="muted">No adjuntó archivo</dd>
            </div>
          )}
        </dl>

        {nutri.estadoValidacion === "RECHAZADA" && nutri.notasValidacion && (
          <p className="ficha__nota">Motivo del rechazo: {nutri.notasValidacion}</p>
        )}

        <section className="ficha__bloque">
          <h3 className="ficha__titulo">Porcentajes</h3>
          <div className="ficha__pcts">
            <label className="field">
              <span>Descuento de bonos (%)</span>
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
            <p className="ficha__hint">
              Aplican a los bonos futuros: los emitidos conservan su porcentaje.
            </p>
          </div>
        </section>

        {error && <div className="alert alert--error">{error}</div>}

        {/* Zona de cuenta: acciones raras y difíciles de revertir, separadas de lo de todos los
            días. Cada botón lleva su aclaración al lado, en una línea — sin eso "Desactivar" y
            "Borrar" se parecen demasiado y la diferencia se descubre apretando. */}
        {!pendiente && (
          <section className="ficha__bloque">
            <h3 className="ficha__titulo">Cuenta</h3>
            <div className="ficha__cuenta">
              <div className="ficha__cuenta-fila">
                <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={onResetPassword}>
                  Cambiar contraseña
                </button>
                <span className="ficha__hint">No hay recuperación por email.</span>
              </div>

              {aprobada && (
                <div className="ficha__cuenta-fila">
                  {nutri.activo ? (
                    <>
                      <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={onDesactivar}>
                        Desactivar
                      </button>
                      <span className="ficha__hint">Le saca el acceso; conserva todo. Reversible.</span>
                    </>
                  ) : (
                    <>
                      <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={onReactivar}>
                        Reactivar
                      </button>
                      <span className="ficha__hint">Hoy no puede entrar.</span>
                    </>
                  )}
                </div>
              )}

              <div className="ficha__cuenta-fila">
                <button type="button" className="btn btn--sm btn--danger" disabled={busy} onClick={onEliminar}>
                  Borrar
                </button>
                <span className="ficha__hint">
                  Elimina usuario, perfil y pacientes. Sólo si nunca emitió un bono.
                </span>
              </div>
            </div>
          </section>
        )}

        {/* Acción principal, siempre en el mismo lugar: abajo a la derecha. */}
        <div className="ficha__acciones">
          {pendiente && (
            <button type="button" className="btn btn--ghost" disabled={busy} onClick={onRechazar}>
              Rechazar
            </button>
          )}
          <button type="submit" className="btn btn--primary" disabled={busy}>
            {busy ? "Guardando…" : pendiente ? "Aprobar solicitud" : "Guardar cambios"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
