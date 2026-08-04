// C-17 — mi perfil: datos de la cuenta + foto (el circulito del navbar).
// Los datos son de sólo lectura por ahora: cambiarlos toca la validación de matrícula, así que
// eso se maneja con el admin. Lo único editable acá es la foto.

import { useRef, useState } from "react";
import { ApiRequestError } from "../api/client";
import { borrarFoto, subirFoto } from "../api/perfil";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/ui/Avatar";
import { Icon } from "../components/ui/Icon";
import { useToast } from "../components/ui/Toast";

const MAX_MB = 8;

export function Perfil() {
  const { me, refrescar } = useAuth();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

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
    if (!window.confirm("¿Quitar tu foto de perfil?")) return;
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

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Mi perfil</h1>
          <p className="muted">Tu foto y los datos con los que validamos tu cuenta.</p>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 620 }}>
        <div className="perfil__foto">
          <Avatar me={me} size={96} />
          <div>
            <button className="btn btn--ghost btn--sm" disabled={busy} onClick={() => input.current?.click()}>
              <Icon name="plus" size={15} />
              {me?.foto ? "Cambiar foto" : "Subir foto"}
            </button>
            {me?.foto && (
              <button className="btn btn--ghost btn--sm" disabled={busy} onClick={quitar}
                      style={{ marginLeft: "0.5rem" }}>
                Quitar
              </button>
            )}
            <p className="muted" style={{ fontSize: "0.78rem", margin: "0.5rem 0 0" }}>
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

        <dl className="detalle__meta" style={{ marginTop: "1.4rem" }}>
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
        </dl>
        <p className="muted" style={{ fontSize: "0.78rem" }}>
          ¿Necesitás corregir tus datos o tu matrícula? Escribinos y lo actualizamos: son los datos
          con los que se validó tu cuenta.
        </p>
      </div>
    </section>
  );
}
