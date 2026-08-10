// F.6 (parte pública) — Registro de nutricionista. Form público → POST /registro → queda PENDIENTE
// de aprobación. Rediseño 2026-07: split-screen (panel de validación + formulario).
// La bandeja admin de aprobación queda para más adelante.

import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiRequestError } from "../api/client";
import { registrar } from "../api/registro";
import { config } from "../config";
import { CONDICIONES_FISCALES } from "../types/registro";
import { Icon } from "../components/ui/Icon";

const E164 = /^\+[1-9]\d{7,14}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Field =
  | "nombre"
  | "apellido"
  | "email"
  | "telefono"
  | "matricula"
  | "dni"
  | "cuit"
  | "condicionFiscal"
  | "password"
  | "password2";
type Errors = Partial<Record<Field | "terms" | "archivo", string>>;

const DNI = /^[0-9]{7,9}$/;
const CUIT = /^[0-9]{2}-?[0-9]{8}-?[0-9]$/;
/** Lo que acepta el backend para la matrícula (C-08). */
const TIPOS_MATRICULA = ["application/pdf", "image/jpeg", "image/png", "image/webp"];
const MAX_MB = 5;

export function Registro() {
  const [f, setF] = useState<Record<Field, string>>({
    nombre: "",
    apellido: "",
    email: "",
    telefono: "",
    matricula: "",
    dni: "",
    cuit: "",
    condicionFiscal: "",
    password: "",
    password2: "",
  });
  const [archivo, setArchivo] = useState<File | null>(null);
  const [terms, setTerms] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  const set =
    (k: Field) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setF((prev) => ({ ...prev, [k]: e.target.value }));

  function validate(): Errors {
    const e: Errors = {};
    if (!f.nombre.trim()) e.nombre = "Requerido.";
    if (!f.apellido.trim()) e.apellido = "Requerido.";
    if (!f.email.trim()) e.email = "Requerido.";
    else if (!EMAIL.test(f.email.trim())) e.email = "Email inválido.";
    if (!f.telefono.trim()) e.telefono = "Requerido.";
    else if (!E164.test(f.telefono.trim())) e.telefono = "Formato E.164, ej. +5491133334444.";
    if (!f.matricula.trim()) e.matricula = "Requerido.";
    if (!f.dni.trim()) e.dni = "Requerido.";
    else if (!DNI.test(f.dni.trim())) e.dni = "Sólo números, sin puntos.";
    if (!f.cuit.trim()) e.cuit = "Requerido.";
    else if (!CUIT.test(f.cuit.trim())) e.cuit = "11 dígitos, ej. 27-12345678-4.";
    if (!f.condicionFiscal) e.condicionFiscal = "Elegí una opción.";
    // El adjunto es lo que el admin mira para validar la matrícula: sin eso no hay solicitud.
    if (!archivo) e.archivo = "Subí tu matrícula o título.";
    else if (!TIPOS_MATRICULA.includes(archivo.type)) e.archivo = "Tiene que ser un PDF o una imagen.";
    else if (archivo.size > MAX_MB * 1024 * 1024) e.archivo = `El archivo supera los ${MAX_MB} MB.`;
    if (!f.password) e.password = "Requerido.";
    else if (f.password.length < 8) e.password = "Mínimo 8 caracteres.";
    if (f.password2 !== f.password) e.password2 = "Las contraseñas no coinciden.";
    if (!terms) e.terms = "Debés aceptar los términos.";
    return e;
  }

  async function onSubmit(ev: FormEvent) {
    ev.preventDefault();
    setSubmitError(null);
    const errs = validate();
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setSaving(true);
    try {
      await registrar(
        {
          nombre: f.nombre.trim(),
          apellido: f.apellido.trim(),
          email: f.email.trim(),
          telefono: f.telefono.trim(),
          matricula: f.matricula.trim(),
          dni: f.dni.trim(),
          cuit: f.cuit.trim(),
          condicionFiscal: f.condicionFiscal,
          password: f.password,
        },
        archivo!,
      );
      setDone(true);
    } catch (err) {
      setSubmitError(
        err instanceof ApiRequestError ? err.message : "No se pudo enviar la solicitud.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (done) {
    return (
      <div className="login">
        <div className="card login__card exito__card">
          <div className="exito__check">
            <Icon name="check-circle" size={28} />
          </div>
          <h1>Solicitud enviada</h1>
          <p className="muted">
            Tu registro quedó <strong>pendiente de aprobación</strong>. Te avisaremos por email cuando
            un administrador valide tu cuenta.
          </p>
          <div className="exito__actions">
            <Link className="btn btn--primary" to="/">
              Volver al inicio
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="auth">
      {/* Panel izquierdo: cómo funciona la validación */}
      <aside className="auth__brand">
        <div className="auth__brand-top">
          <span className="auth__brand-badge">
            <Icon name="leaf" />
          </span>
          <span className="auth__brand-name">NutriApp</span>
        </div>

        <div className="auth__value">
          <h2 className="auth__headline">Cada cuenta se valida a mano.</h2>
          <p className="auth__lead">
            Verificamos tu matrícula antes de habilitarte. Suele tardar entre 24 y 48 horas hábiles.
          </p>
          <ul className="auth__steps">
            <li>
              <b>1.</b> Completás tus datos y número de matrícula.
            </li>
            <li>
              <b>2.</b> El administrador verifica y aprueba la cuenta.
            </li>
            <li>
              <b>3.</b> Recibís un mail y ya podés emitir recetas.
            </li>
          </ul>
        </div>

        <p className="auth__note">Tus datos se usan solo para la validación profesional.</p>
      </aside>

      {/* Panel derecho: formulario.
          El orden de los campos está pensado para que la grilla de 3 columnas cierre en filas
          completas y el form entre en una pantalla sin scroll: los cortos van de a tres, y email y
          adjunto ocupan el ancho completo. */}
      <main className="auth__panel">
        <form className="auth__card auth__card--registro" onSubmit={onSubmit} noValidate>
          <h1 className="auth__title">Solicitar acceso</h1>
          <p className="auth__subtitle">Todos los campos son obligatorios.</p>

          <div className="form-grid form-grid--registro">
            <label className="field">
              <span>Nombre</span>
              <input value={f.nombre} onChange={set("nombre")} autoFocus />
              {errors.nombre && <small className="auth__err">{errors.nombre}</small>}
            </label>
            <label className="field">
              <span>Apellido</span>
              <input value={f.apellido} onChange={set("apellido")} />
              {errors.apellido && <small className="auth__err">{errors.apellido}</small>}
            </label>
            <label className="field">
              <span>Teléfono</span>
              <input placeholder="+5491133334444" value={f.telefono} onChange={set("telefono")} />
              {errors.telefono && <small className="auth__err">{errors.telefono}</small>}
            </label>
            <label className="field field--span2">
              <span>Email profesional</span>
              <input type="email" value={f.email} onChange={set("email")} />
              {errors.email && <small className="auth__err">{errors.email}</small>}
            </label>
            <label className="field">
              <span>DNI</span>
              <input placeholder="30111222" value={f.dni} onChange={set("dni")} inputMode="numeric" />
              {errors.dni && <small className="auth__err">{errors.dni}</small>}
            </label>
            <label className="field">
              <span>Matrícula nacional</span>
              <input placeholder="MN 12.483" value={f.matricula} onChange={set("matricula")} />
              {errors.matricula && <small className="auth__err">{errors.matricula}</small>}
            </label>
            <label className="field">
              <span>CUIT</span>
              <input placeholder="27-30111222-4" value={f.cuit} onChange={set("cuit")} />
              {errors.cuit && <small className="auth__err">{errors.cuit}</small>}
            </label>
            <label className="field">
              <span>Condición fiscal</span>
              <select value={f.condicionFiscal} onChange={set("condicionFiscal")}>
                <option value="">Elegí una opción…</option>
                {CONDICIONES_FISCALES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
              {errors.condicionFiscal && <small className="auth__err">{errors.condicionFiscal}</small>}
            </label>
            <label className="field">
              <span>Contraseña</span>
              <input
                type="password"
                autoComplete="new-password"
                value={f.password}
                onChange={set("password")}
              />
              {errors.password && <small className="auth__err">{errors.password}</small>}
            </label>
            <label className="field">
              <span>Repetir contraseña</span>
              <input
                type="password"
                autoComplete="new-password"
                value={f.password2}
                onChange={set("password2")}
              />
              {errors.password2 && <small className="auth__err">{errors.password2}</small>}
            </label>
            <label className="field field--full field--file">
              <span>
                Matrícula o título <small className="muted">· PDF o imagen, hasta {MAX_MB} MB</small>
              </span>
              <input
                type="file"
                accept=".pdf,image/jpeg,image/png,image/webp"
                onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
              />
              {errors.archivo && <small className="auth__err">{errors.archivo}</small>}
            </label>
          </div>

          <label className="auth__terms">
            <input type="checkbox" checked={terms} onChange={(e) => setTerms(e.target.checked)} />
            <span>
              Declaro que la matrícula informada es propia y acepto los términos de uso de la
              plataforma.
            </span>
          </label>
          {errors.terms && <small className="auth__err">{errors.terms}</small>}

          {submitError && <div className="alert alert--error">{submitError}</div>}

          <button className="auth__submit" type="submit" disabled={saving}>
            {saving ? "Enviando…" : "Enviar solicitud"}
          </button>

          {/* En modo pre-lanzamiento no se ofrece login: `/` es la landing "Próximamente". */}
          {!config.comingSoon && (
            <p className="auth__foot">
              ¿Ya tenés cuenta? <Link to="/">Ingresar</Link>
            </p>
          )}
        </form>
      </main>
    </div>
  );
}
