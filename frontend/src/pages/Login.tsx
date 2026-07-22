// Pantalla de login (ROPC). Si ya hay sesión, redirige al dashboard.

import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function Login() {
  const { me, initializing, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!initializing && me) return <Navigate to="/dashboard" replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "No se pudo iniciar sesión.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth">
      {/* Panel izquierdo: marca (hueco para el logo cuando esté) */}
      <aside className="auth__brand">
        <div className="auth__brand-inner">
          {/* logo va acá cuando lo tengamos */}
          <h1 className="auth__brand-name">NutriApp</h1>
          <p className="auth__brand-sub">Recetas digitales para nutricionistas</p>
        </div>
      </aside>

      {/* Panel derecho: formulario */}
      <main className="auth__panel">
        <form className="auth__card" onSubmit={onSubmit}>
          <h1 className="auth__title">Bienvenido</h1>
          <p className="auth__subtitle">Ingresá con tu cuenta</p>

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

          <p className="registro__foot muted">
            ¿Sos nutricionista nuevo? <Link to="/registro">Registrate</Link>
          </p>
        </form>
      </main>
    </div>
  );
}
