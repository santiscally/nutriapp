// F.6 (parte pública) — Registro de nutricionista. Form público → POST /registro → queda PENDIENTE de aprobación.
// La bandeja admin de aprobación queda para Fase 3 (endpoint backend aún no implementado).

import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiRequestError } from "../api/client";
import { registrar } from "../api/registro";

const E164 = /^\+[1-9]\d{7,14}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Field = "nombre" | "apellido" | "email" | "telefono" | "matricula" | "password";
type Errors = Partial<Record<Field, string>>;

export function Registro() {
  const [f, setF] = useState<Record<Field, string>>({
    nombre: "",
    apellido: "",
    email: "",
    telefono: "",
    matricula: "",
    password: "",
  });
  const [errors, setErrors] = useState<Errors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  const set = (k: Field) => (e: React.ChangeEvent<HTMLInputElement>) =>
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
    if (!f.password) e.password = "Requerido.";
    else if (f.password.length < 8) e.password = "Mínimo 8 caracteres.";
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
      await registrar({
        nombre: f.nombre.trim(),
        apellido: f.apellido.trim(),
        email: f.email.trim(),
        telefono: f.telefono.trim(),
        matricula: f.matricula.trim(),
        password: f.password,
      });
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
          <div className="exito__check">✓</div>
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
    <div className="login">
      <form className="card login__card registro__card" onSubmit={onSubmit} noValidate>
        <h1 className="login__brand">Crear cuenta</h1>
        <p className="login__subtitle">Registro de nutricionista</p>

        <div className="form-grid">
          <label className="field">
            <span>Nombre *</span>
            <input value={f.nombre} onChange={set("nombre")} autoFocus />
            {errors.nombre && <small className="field__err">{errors.nombre}</small>}
          </label>
          <label className="field">
            <span>Apellido *</span>
            <input value={f.apellido} onChange={set("apellido")} />
            {errors.apellido && <small className="field__err">{errors.apellido}</small>}
          </label>
          <label className="field field--full">
            <span>Email *</span>
            <input type="email" value={f.email} onChange={set("email")} />
            {errors.email && <small className="field__err">{errors.email}</small>}
          </label>
          <label className="field">
            <span>Teléfono *</span>
            <input placeholder="+5491133334444" value={f.telefono} onChange={set("telefono")} />
            {errors.telefono && <small className="field__err">{errors.telefono}</small>}
          </label>
          <label className="field">
            <span>Matrícula *</span>
            <input placeholder="MN 1234" value={f.matricula} onChange={set("matricula")} />
            {errors.matricula && <small className="field__err">{errors.matricula}</small>}
          </label>
          <label className="field field--full">
            <span>Contraseña *</span>
            <input
              type="password"
              autoComplete="new-password"
              value={f.password}
              onChange={set("password")}
            />
            {errors.password && <small className="field__err">{errors.password}</small>}
          </label>
        </div>

        {submitError && <div className="alert alert--error">{submitError}</div>}

        <button className="btn btn--primary" type="submit" disabled={saving}>
          {saving ? "Enviando…" : "Registrarme"}
        </button>
        <p className="registro__foot muted">
          ¿Ya tenés cuenta? <Link to="/">Iniciar sesión</Link>
        </p>
      </form>
    </div>
  );
}
