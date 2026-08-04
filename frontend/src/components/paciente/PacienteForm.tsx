// Form de alta/edición de paciente. email y whatsapp obligatorios (whatsapp en formato E.164).
//
// Fecha de nacimiento y notas se editan igual que el resto: el GET ya las devuelve, así que se
// pueden precargar sin riesgo de pisarlas. Antes sólo estaban en el alta —el response no las
// traía— y eso dejaba las notas escritas una vez y nunca más visibles.

import { useState, type FormEvent } from "react";
import { ApiRequestError } from "../../api/client";
import { actualizarPaciente, crearPaciente } from "../../api/pacientes";
import type { Paciente } from "../../types/paciente";

// E.164: '+' seguido de 8 a 15 dígitos, sin empezar en 0.
const E164 = /^\+[1-9]\d{7,14}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Errors = Partial<Record<"nombre" | "apellido" | "email" | "whatsapp", string>>;

export function PacienteForm({
  paciente,
  onSaved,
  onCancel,
}: {
  paciente: Paciente | null; // null = alta
  onSaved: () => void;
  onCancel: () => void;
}) {
  const isEdit = paciente !== null;
  const [nombre, setNombre] = useState(paciente?.nombre ?? "");
  const [apellido, setApellido] = useState(paciente?.apellido ?? "");
  const [email, setEmail] = useState(paciente?.email ?? "");
  const [whatsapp, setWhatsapp] = useState(paciente?.whatsapp ?? "");
  const [fechaNacimiento, setFechaNacimiento] = useState(paciente?.fechaNacimiento ?? "");
  const [notas, setNotas] = useState(paciente?.notas ?? "");

  const [errors, setErrors] = useState<Errors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function validate(): Errors {
    const e: Errors = {};
    if (!nombre.trim()) e.nombre = "Requerido.";
    if (!apellido.trim()) e.apellido = "Requerido.";
    if (!email.trim()) e.email = "Requerido.";
    else if (!EMAIL.test(email.trim())) e.email = "Email inválido.";
    if (!whatsapp.trim()) e.whatsapp = "Requerido.";
    else if (!E164.test(whatsapp.trim()))
      e.whatsapp = "Formato E.164, ej. +5491133334444.";
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
      const body = {
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        email: email.trim(),
        whatsapp: whatsapp.trim(),
        fechaNacimiento: fechaNacimiento || undefined,
        notas: notas.trim() || undefined,
      };
      if (isEdit) {
        await actualizarPaciente(paciente.id, body);
      } else {
        await crearPaciente(body);
      }
      onSaved();
    } catch (err) {
      setSubmitError(
        err instanceof ApiRequestError ? err.message : "No se pudo guardar el paciente.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <div className="form-grid">
        <label className="field">
          <span>Nombre *</span>
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} autoFocus />
          {errors.nombre && <small className="field__err">{errors.nombre}</small>}
        </label>
        <label className="field">
          <span>Apellido *</span>
          <input value={apellido} onChange={(e) => setApellido(e.target.value)} />
          {errors.apellido && <small className="field__err">{errors.apellido}</small>}
        </label>
        <label className="field">
          <span>Email *</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          {errors.email && <small className="field__err">{errors.email}</small>}
        </label>
        <label className="field">
          <span>WhatsApp *</span>
          <input
            placeholder="+5491133334444"
            value={whatsapp}
            onChange={(e) => setWhatsapp(e.target.value)}
          />
          {errors.whatsapp && <small className="field__err">{errors.whatsapp}</small>}
        </label>

        <label className="field">
          <span>Fecha de nacimiento</span>
          <input
            type="date"
            value={fechaNacimiento}
            onChange={(e) => setFechaNacimiento(e.target.value)}
          />
        </label>
        <label className="field field--full">
          <span>Notas</span>
          <textarea
            rows={3}
            placeholder="Objetivos, alergias, indicaciones que quieras tener a mano."
            value={notas}
            onChange={(e) => setNotas(e.target.value)}
          />
        </label>
      </div>

      {submitError && <div className="alert alert--error">{submitError}</div>}

      <div className="form-actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel}>
          Cancelar
        </button>
        <button type="submit" className="btn btn--primary" disabled={saving}>
          {saving ? "Guardando…" : isEdit ? "Guardar cambios" : "Crear paciente"}
        </button>
      </div>
    </form>
  );
}
