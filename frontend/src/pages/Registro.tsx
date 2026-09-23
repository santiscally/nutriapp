// F.6 (parte pública) — Registro de nutricionista. Form público → POST /registro → queda PENDIENTE
// de aprobación. Rediseño 2026-07: split-screen (panel de validación + formulario).
// La bandeja admin de aprobación queda para más adelante.

import { useCallback, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiRequestError } from "../api/client";
import { listarProfesiones, reenviarVerificacion, registrar } from "../api/registro";
import { config } from "../config";
import { CONDICIONES_FISCALES, JURISDICCIONES } from "../types/registro";
import { useFetch } from "../hooks/useFetch";
import { Icon } from "../components/ui/Icon";
import { Logo } from "../components/ui/Logo";

const E164 = /^\+[1-9]\d{7,14}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Field =
  | "nombre"
  | "apellido"
  | "email"
  | "telefono"
  | "jurisdiccion"
  | "profesion"
  | "matricula"
  | "dni"
  | "cuit"
  | "condicionFiscal"
  | "password"
  | "password2";
type Errors = Partial<Record<Field | "terms" | "archivo", string>>;

const DNI = /^[0-9]{7,9}$/;
// F-04 — el CUIT se tipea sólo en dígitos (el backend acepta con o sin guiones y normaliza).
const CUIT = /^[0-9]{11}$/;
// F-03 — la matrícula es un número: ni letras ni puntos ni guiones.
const MATRICULA = /^[0-9]{1,15}$/;
/** Deja pasar sólo dígitos mientras se tipea (F-03 / F-04). */
const soloDigitos = (v: string) => v.replace(/[^0-9]/g, "");
/** Lo que acepta el backend para la matrícula (C-08). */
const TIPOS_MATRICULA = ["application/pdf", "image/jpeg", "image/png", "image/webp"];
const MAX_MB = 5;

export function Registro() {
  const [f, setF] = useState<Record<Field, string>>({
    nombre: "",
    apellido: "",
    email: "",
    telefono: "",
    jurisdiccion: "",
    profesion: "",
    matricula: "",
    dni: "",
    cuit: "",
    condicionFiscal: "",
    password: "",
    password2: "",
  });
  // F-06 — las profesiones salen del backend (endpoint público): son 76 y el cliente las puede
  // cambiar sin que haya que recompilar el front.
  const fetchProfesiones = useCallback((s: AbortSignal) => listarProfesiones(s), []);
  const { data: profesiones, error: errorProfesiones } = useFetch(fetchProfesiones, []);

  const [archivo, setArchivo] = useState<File | null>(null);
  const [terms, setTerms] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);
  const [reenvio, setReenvio] = useState<"idle" | "enviando" | "enviado">("idle");

  const set =
    (k: Field) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setF((prev) => ({ ...prev, [k]: e.target.value }));

  /** Igual que set(), pero descarta todo lo que no sea un dígito (F-03 matrícula, F-04 CUIT). */
  const setNum =
    (k: Field) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setF((prev) => ({ ...prev, [k]: soloDigitos(e.target.value) }));

  function validate(): Errors {
    const e: Errors = {};
    if (!f.nombre.trim()) e.nombre = "Requerido.";
    if (!f.apellido.trim()) e.apellido = "Requerido.";
    if (!f.email.trim()) e.email = "Requerido.";
    else if (!EMAIL.test(f.email.trim())) e.email = "Email inválido.";
    if (!f.telefono.trim()) e.telefono = "Requerido.";
    else if (!E164.test(f.telefono.trim())) e.telefono = "Formato incorrecto. Ej: +5491133334444";
    if (!f.jurisdiccion.trim()) e.jurisdiccion = "Requerido.";
    if (!f.profesion.trim()) e.profesion = "Elegí tu profesión.";
    if (!f.matricula.trim()) e.matricula = "Requerido.";
    else if (!MATRICULA.test(f.matricula.trim())) e.matricula = "Sólo números. Ej: 12483";
    if (!f.dni.trim()) e.dni = "Requerido.";
    else if (!DNI.test(f.dni.trim())) e.dni = "Sólo números, sin puntos.";
    if (!f.cuit.trim()) e.cuit = "Requerido.";
    else if (!CUIT.test(f.cuit.trim())) e.cuit = "Sólo números, 11 dígitos. Ej: 27123456784";
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
          // S-11 (V014) — ya no se concatena: jurisdicción y profesión son columnas propias y
          // `matricula` es sólo el número.
          matricula: f.matricula.trim(),
          jurisdiccion: f.jurisdiccion.trim(),
          profesion: f.profesion.trim(),
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
          <p className="muted">
            Además te mandamos un mail a <strong>{f.email.trim()}</strong> para{" "}
            <strong>validar tu casilla</strong>: sin ese paso no vas a poder ingresar, aunque tu cuenta
            ya esté aprobada. Si no lo ves, mirá en spam y en promociones.
          </p>
          <p className="muted">
            {reenvio === "enviado" ? (
              "Listo: te lo reenviamos."
            ) : (
              <button
                type="button"
                className="link-button"
                disabled={reenvio === "enviando"}
                onClick={async () => {
                  setReenvio("enviando");
                  try {
                    await reenviarVerificacion(f.email.trim());
                  } finally {
                    setReenvio("enviado");
                  }
                }}
              >
                {reenvio === "enviando" ? "Reenviando…" : "No me llegó: reenviar el mail"}
              </button>
            )}
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
          <Logo size={36} />
          <span className="auth__brand-name">BonosApp</span>
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
              <b>3.</b> Recibís un mail y ya podés emitir bonos profesionales.
            </li>
          </ul>
        </div>

        <p className="auth__note">Tus datos se usan solo para la validación profesional.</p>
        <p className="auth__note auth__contacto">
          Por cualquier consulta, envianos un mail a{" "}
          <a href={`mailto:${config.contactoEmail}`}>{config.contactoEmail}</a>
        </p>
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
              <span>Whatsapp / Teléfono</span>
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
              <input placeholder="30111222" value={f.dni} onChange={setNum("dni")} inputMode="numeric" />
              {errors.dni && <small className="auth__err">{errors.dni}</small>}
            </label>
            <label className="field">
              <span>Jurisdicción de matrícula</span>
              <select value={f.jurisdiccion} onChange={set("jurisdiccion")}>
                <option value="">Elegí una opción…</option>
                {JURISDICCIONES.map((j) => (
                  <option key={j} value={j}>
                    {j}
                  </option>
                ))}
              </select>
              {errors.jurisdiccion && <small className="auth__err">{errors.jurisdiccion}</small>}
            </label>
            <label className="field">
              <span>Profesión</span>
              <select value={f.profesion} onChange={set("profesion")} disabled={!profesiones}>
                <option value="">
                  {profesiones ? "Elegí una opción…" : "Cargando…"}
                </option>
                {profesiones?.map((pr) => (
                  <option key={pr.id} value={pr.nombre}>
                    {pr.nombre}
                  </option>
                ))}
              </select>
              {/* Si el listado no carga, el registro no se puede completar: conviene decirlo acá
                  y no dejar un desplegable vacío sin explicación. */}
              {errorProfesiones && (
                <small className="auth__err">No se pudo cargar la lista de profesiones.</small>
              )}
              {errors.profesion && <small className="auth__err">{errors.profesion}</small>}
            </label>
            <label className="field">
              <span>N° de matrícula</span>
              <input
                placeholder="12483"
                value={f.matricula}
                onChange={setNum("matricula")}
                inputMode="numeric"
              />
              {errors.matricula && <small className="auth__err">{errors.matricula}</small>}
            </label>
            <label className="field">
              <span>CUIT</span>
              <input
                placeholder="27301112224"
                value={f.cuit}
                onChange={setNum("cuit")}
                inputMode="numeric"
                maxLength={11}
              />
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
              Declaro que la matrícula informada es propia y acepto los{" "}
              {/* F-07 — pestaña nueva: si se navega en la misma, se pierde el formulario a medio
                  llenar (incluido el archivo adjunto, que el browser no repuebla). */}
              <a href={config.terminosUrl} target="_blank" rel="noopener noreferrer">
                términos de uso
              </a>{" "}
              de la plataforma.
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
