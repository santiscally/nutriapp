// Landing pre-lanzamiento ("próximamente"). Se muestra en `/` cuando VITE_COMING_SOON=true,
// para poder publicar bonosapp.com.ar antes de que la app esté terminada: el visitante ve la
// propuesta de valor y lo único que puede hacer es solicitar acceso (`/registro`).
//
// El login NO se linkea desde acá — vive en `/ingresar` (sin link, para el equipo y las demos).
// No es una barrera de seguridad, sólo saca la puerta de entrada de la vista pública: la barrera
// real es que toda cuenta nueva nace PENDIENTE y deshabilitada en Keycloak hasta que el admin
// la aprueba. Apagar el modo = VITE_COMING_SOON=false + rebuild, sin tocar código.

import { Link } from "react-router-dom";
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

export function Proximamente() {
  return (
    <div className="soon">
      <div className="soon__glow" aria-hidden="true" />

      <main className="soon__inner">
        <div className="soon__brand">
          <Logo size={36} />
          <span className="auth__brand-name">BonosApp</span>
        </div>

        <p className="soon__eyebrow">
          <Icon name="clock" size={14} />
          Próximamente
        </p>

        <h1 className="soon__title">Recomendaciones Profesionales, con beneficios exclusivos.</h1>

        <p className="soon__lead">
          Próximamente estará disponible la plataforma digital para profesionales de nutrición, salud
          y bienestar; en la que podrán emitir bonos profesionales, con descuentos y beneficios
          exclusivos para sus pacientes.
        </p>

        <ul className="soon__pasos">
          {PASOS.map((p) => (
            <li className="soon__paso" key={p.titulo}>
              <span className="soon__paso-icon">
                <Icon name={p.icon} size={18} />
              </span>
              <b>{p.titulo}</b>
              <span className="soon__paso-texto">{p.texto}</span>
            </li>
          ))}
        </ul>

        <Link className="soon__cta" to="/registro">
          Solicitar acceso
        </Link>

        <p className="soon__note">
          Cada cuenta se valida individualmente: verificamos tu matrícula antes de habilitarte. Te
          avisaremos por mail cuando la misma esté habilitada.
        </p>

        <p className="soon__contacto">
          Por cualquier consulta, envianos un mail a{" "}
          <a href={`mailto:${config.contactoEmail}`}>{config.contactoEmail}</a>
        </p>
      </main>

      <footer className="soon__footer">
        <span>© {new Date().getFullYear()} BonosApp</span>
        <a
          className="powered-by"
          href="https://simpleapps.com.ar"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="powered by Simple Apps"
        >
          <span className="powered-text">powered by</span>
          <span className="powered-logo">{"<s/a>"}</span>
        </a>
      </footer>
    </div>
  );
}
