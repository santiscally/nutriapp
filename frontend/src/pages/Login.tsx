// Pantalla de login (ROPC). Si ya hay sesión, redirige al dashboard.

import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { homeDe } from "../lib/home";
import { Icon } from "../components/ui/Icon";

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
      {/* Panel izquierdo: marca + propuesta de valor */}
      <aside className="auth__brand">
        <div className="auth__brand-top">
          <span className="auth__brand-badge">
            <Icon name="leaf" />
          </span>
          <span className="auth__brand-name">NutriApp</span>
        </div>

        <div className="auth__value">
          <h2 className="auth__headline">Recomendaciones profesionales, con beneficios exclusivos.</h2>
        </div>

        <p className="auth__note">
          Acceso exclusivo para nutricionistas validados por el administrador.
        </p>
      </aside>

      {/* Panel derecho: formulario */}
      <main className="auth__panel">
        <form className="auth__card" onSubmit={onSubmit}>
          <h1 className="auth__title">Bienvenida de nuevo</h1>
          <p className="auth__subtitle">Ingresá con tu email profesional.</p>

          <label className="field">
            <span>Usuario o email</span>
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

          {error && <div className="alert alert--error">{error}</div>}

          <button className="auth__submit" type="submit" disabled={submitting}>
            {submitting ? "Ingresando…" : "Ingresar"}
          </button>

          <p className="auth__foot">
            ¿Sos nutricionista y todavía no tenés cuenta? <Link to="/registro">Solicitar acceso</Link>
          </p>
        </form>
      </main>
    </div>
  );
}
