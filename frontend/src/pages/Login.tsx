// Pantalla de login (ROPC). Si ya hay sesión, redirige al dashboard.
//
// El panel izquierdo absorbió la propuesta de valor que vivía en la landing "Próximamente"
// (`Proximamente.tsx`): al apagar el pre-lanzamiento, `/` pasa a ser este login y esa
// información — qué es BonosApp, los 3 pasos y la casilla de contacto — no tenía dónde
// mostrarse. La landing queda en el repo por si hay que volver a encenderla.

import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { homeDe } from "../lib/home";
import { Icon } from "../components/ui/Icon";
import { Logo } from "../components/ui/Logo";
import { config } from "../config";

const PASOS = [
  {
    icon: "file-plus",
    titulo: "Emitís bono profesional",
    texto: "Elegís paciente y productos, y se emite el descuento exclusivo.",
  },
  {
    icon: "send",
    titulo: "Tu paciente adquiere",
    texto:
      "Le llega por mail y/o WhatsApp el bono profesional, que podrá usar en una tienda especializada.",
  },
  {
    icon: "trending-up",
    titulo: "Seguís todo acá",
    texto:
      "Podés ver el seguimiento de los bonos, y si el paciente convierte, recibirás beneficios exclusivos.",
  },
] as const;

export function Login() {
  const { me, initializing, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // C-07: cada rol arranca en su propia pantalla (el admin no tiene panel de recetas).
  if (!initializing && me) return <Navigate to={homeDe(me)} replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const sesion = await login(email, password);
      navigate(homeDe(sesion), { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "No se pudo iniciar sesión.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth">
      {/* Panel izquierdo: marca + propuesta de valor (ex landing "Próximamente") */}
      <aside className="auth__brand">
        <div className="auth__brand-top">
          <Logo size={36} />
          <span className="auth__brand-name">BonosApp</span>
        </div>

        <div className="auth__value">
          <h2 className="auth__headline">Recomendaciones profesionales, con beneficios exclusivos.</h2>

          <p className="auth__lead">
            La plataforma digital para profesionales de nutrición, salud y bienestar: emitís bonos
            profesionales, con descuentos y beneficios exclusivos para tus pacientes.
          </p>

          <ul className="auth__steps auth__steps--icon">
            {PASOS.map((p) => (
              <li key={p.titulo}>
                <span className="auth__step-icon">
                  <Icon name={p.icon} size={16} />
                </span>
                <span>
                  <b>{p.titulo}</b>
                  <span className="auth__step-texto">{p.texto}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <p className="auth__note">
            Cada cuenta se valida individualmente: verificamos tu matrícula antes de habilitarte. Te
            avisaremos por mail cuando la misma esté habilitada.
          </p>
          <p className="auth__note auth__contacto">
            Por cualquier consulta, envianos un mail a{" "}
            <a href={`mailto:${config.contactoEmail}`}>{config.contactoEmail}</a>
          </p>
        </div>
      </aside>

      {/* Panel derecho: formulario */}
      <main className="auth__panel">
        <form className="auth__card" onSubmit={onSubmit}>
          <h1 className="auth__title">Bienvenida</h1>
          <p className="auth__subtitle">Ingresá con tu email profesional.</p>

          <label className="field">
            <span>Email</span>
            <input
              type="text"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>

          <label className="field">
            <span>Contraseña</span>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>

          <p className="auth__olvide">
            <Link to="/recuperar-password">¿Olvidaste tu contraseña?</Link>
          </p>

          {error && <div className="alert alert--error">{error}</div>}

          <button className="auth__submit" type="submit" disabled={submitting}>
            {submitting ? "Ingresando…" : "Ingresar"}
          </button>

          <p className="auth__foot">
            ¿Sos profesional y todavía no tenés cuenta? <Link to="/registro">Solicitar acceso</Link>
          </p>
        </form>
      </main>
    </div>
  );
}
