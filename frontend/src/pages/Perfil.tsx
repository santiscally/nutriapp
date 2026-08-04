// Mi perfil: foto + contraseña + los datos con los que se validó la cuenta.
//
// Lo único que cada quien puede cambiar de sí mismo es la foto y la contraseña. El resto (nombre,
// matrícula, datos fiscales, porcentajes) lo toca el admin: son justamente los datos que se
// validaron al aprobar la cuenta.

import { useRef, useState, type FormEvent } from "react";
import { ApiRequestError } from "../api/client";
import { borrarFoto, cambiarPassword, subirFoto } from "../api/perfil";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/ui/Avatar";
import { useDialog } from "../components/ui/Dialog";
import { Icon } from "../components/ui/Icon";
import { useToast } from "../components/ui/Toast";

const MAX_MB = 8;
const MIN_PASSWORD = 8;

export function Perfil() {
  const { me, refrescar } = useAuth();
  const toast = useToast();
  const { confirmar } = useDialog();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [repetir, setRepetir] = useState("");
  const [passBusy, setPassBusy] = useState(false);
  const [passError, setPassError] = useState<string | null>(null);

  async function onArchivo(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = ""; // permite volver a elegir el mismo archivo si falló
    if (!file) return;
    if (file.size > MAX_MB * 1024 * 1024) {
      toast.error(`La imagen supera los ${MAX_MB} MB.`);
      return;
    }
    setBusy(true);
    try {
      await subirFoto(file);
      await refrescar();
      toast.success("Foto actualizada.");
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "No se pudo subir la foto.");
    } finally {
      setBusy(false);
    }
  }

  async function quitar() {
    const ok = await confirmar({
      titulo: "¿Quitar tu foto de perfil?",
      mensaje: "Vas a volver a aparecer con tus iniciales. Podés subir otra cuando quieras.",
      confirmar: "Quitar foto",
    });
    if (!ok) return;
    setBusy(true);
    try {
      await borrarFoto();
      await refrescar();
      toast.success("Foto eliminada.");
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "No se pudo quitar la foto.");
    } finally {
      setBusy(false);
    }
  }

  async function onPassword(e: FormEvent) {
    e.preventDefault();
    setPassError(null);
    if (nueva.length < MIN_PASSWORD) {
      setPassError(`La contraseña nueva debe tener al menos ${MIN_PASSWORD} caracteres.`);
      return;
    }
    if (nueva !== repetir) {
      setPassError("Las contraseñas nuevas no coinciden.");
      return;
    }
    if (nueva === actual) {
      setPassError("La contraseña nueva tiene que ser distinta de la actual.");
      return;
    }
    setPassBusy(true);
    try {
      await cambiarPassword(actual, nueva);
      setActual("");
      setNueva("");
      setRepetir("");
      toast.success("Contraseña actualizada. La próxima vez entrá con la nueva.");
    } catch (err) {
      setPassError(
        err instanceof ApiRequestError ? err.message : "No se pudo cambiar la contraseña.",
      );
    } finally {
      setPassBusy(false);
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Mi perfil</h1>
          <p className="muted">Tu foto, tu contraseña y los datos con los que validamos tu cuenta.</p>
        </div>
      </div>

      <div className="perfil-grid">
        <div className="card">
          <h2 className="card__title">Foto</h2>
          <div className="perfil__foto">
            <Avatar me={me} size={96} />
            <div className="perfil__foto-acciones">
              <div className="perfil__foto-botones">
                <button
                  className="btn btn--ghost btn--sm"
                  disabled={busy}
                  onClick={() => input.current?.click()}
                >
                  <Icon name="plus" size={15} />
                  {me?.foto ? "Cambiar foto" : "Subir foto"}
                </button>
                {me?.foto && (
                  <button className="btn btn--ghost btn--sm" disabled={busy} onClick={quitar}>
                    Quitar
                  </button>
                )}
              </div>
              <p className="perfil__hint">
                JPG, PNG o WEBP, hasta {MAX_MB} MB. La achicamos automáticamente.
              </p>
            </div>
            <input
              ref={input}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={onArchivo}
              style={{ display: "none" }}
            />
          </div>

          <dl className="detalle__meta perfil__datos">
            <div>
              <dt>Nombre</dt>
              <dd>
                {me?.nombre} {me?.apellido}
              </dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{me?.email}</dd>
            </div>
            <div>
              <dt>Estado de la cuenta</dt>
              <dd>{me?.estadoValidacion === "APROBADA" ? "Aprobada" : me?.estadoValidacion}</dd>
            </div>
            {me?.descuentoPct != null && (
              <div>
                <dt>Descuento de tus recetas</dt>
                <dd>{me.descuentoPct}%</dd>
              </div>
            )}
          </dl>
          <p className="perfil__hint">
            ¿Necesitás corregir tus datos o tu matrícula? Escribinos y lo actualizamos: son los
            datos con los que se validó tu cuenta.
          </p>
        </div>

        <form className="card" onSubmit={onPassword}>
          <h2 className="card__title">Contraseña</h2>
          <p className="perfil__hint" style={{ marginBottom: "1rem" }}>
            Te pedimos la actual para confirmar que sos vos.
          </p>

          <label className="field">
            <span>Contraseña actual</span>
            <input
              type="password"
              autoComplete="current-password"
              value={actual}
              onChange={(e) => setActual(e.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Contraseña nueva</span>
            <input
              type="password"
              autoComplete="new-password"
              value={nueva}
              onChange={(e) => setNueva(e.target.value)}
              minLength={MIN_PASSWORD}
              required
            />
          </label>
          <label className="field">
            <span>Repetir la nueva</span>
            <input
              type="password"
              autoComplete="new-password"
              value={repetir}
              onChange={(e) => setRepetir(e.target.value)}
              minLength={MIN_PASSWORD}
              required
            />
          </label>

          {passError && <div className="alert alert--error">{passError}</div>}

          <button className="btn btn--primary" type="submit" disabled={passBusy}>
            {passBusy ? "Guardando…" : "Cambiar contraseña"}
          </button>
        </form>
      </div>
    </section>
  );
}
