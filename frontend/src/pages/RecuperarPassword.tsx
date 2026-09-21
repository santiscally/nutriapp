// "Olvidé mi contraseña" (S-09). Público: quien entra acá justamente no puede loguearse.
//
// El mensaje de salida es el mismo exista o no la cuenta — el backend responde 204 siempre para
// no delatar qué mails están registrados, y la pantalla no puede contradecirlo.

import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { pedirRecuperoPassword } from "../api/password";
import { ApiRequestError } from "../api/client";
import { Icon } from "../components/ui/Icon";
import { Logo } from "../components/ui/Logo";
import { config } from "../config";

export function RecuperarPassword() {
  const [email, setEmail] = useState("");
  const [enviado, setEnviado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await pedirRecuperoPassword(email.trim());
      setEnviado(true);
    } catch (err) {
      setError(
        err instanceof ApiRequestError ? err.message : "No se pudo procesar el pedido.",
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="auth">
      <aside className="auth__brand">
        <div className="auth__brand-top">
          <Logo size={36} />
          <span className="auth__brand-name">BonosApp</span>
        </div>
        <div className="auth__value">
          <h2 className="auth__headline">Recuperá el acceso a tu cuenta.</h2>
          <p className="auth__lead">
            Te mandamos un enlace por mail para que definas una contraseña nueva. El enlace vence a
            la media hora y se usa una sola vez.
          </p>
        </div>
        <div>
          <p className="auth__note auth__contacto">
            ¿No te llega el mail? Escribinos a{" "}
            <a href={`mailto:${config.contactoEmail}`}>{config.contactoEmail}</a>
          </p>
        </div>
      </aside>

      <main className="auth__panel">
        {enviado ? (
          <div className="auth__card">
            <div className="exito__check">
              <Icon name="check-circle" size={28} />
            </div>
            <h1 className="auth__title">Revisá tu casilla</h1>
            <p className="auth__subtitle">
              Si <strong>{email.trim()}</strong> corresponde a una cuenta activa, va a recibir un
              mail con el enlace para cambiar la contraseña. Mirá también en spam y en la pestaña
              de promociones.
            </p>
            <p className="auth__foot">
              <Link to="/ingresar">Volver a ingresar</Link>
            </p>
          </div>
        ) : (
          <form className="auth__card" onSubmit={onSubmit}>
            <h1 className="auth__title">¿Olvidaste tu contraseña?</h1>
            <p className="auth__subtitle">
              Escribí el email con el que te registraste y te mandamos el enlace.
            </p>

            <label className="field">
              <span>Email</span>
              <input
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>

            {error && <div className="alert alert--error">{error}</div>}

            <button className="auth__submit" type="submit" disabled={enviando}>
              {enviando ? "Enviando…" : "Enviarme el enlace"}
            </button>

            <p className="auth__foot">
              <Link to="/ingresar">Volver a ingresar</Link>
            </p>
          </form>
        )}
      </main>
    </div>
  );
}
