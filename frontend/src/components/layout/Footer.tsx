// Footer del layout autenticado. Contenido real (sin métricas fabricadas): marca + navegación interna.

import { Link } from "react-router-dom";
import { Icon } from "../ui/Icon";

export function Footer() {
  return (
    <footer className="footer">
      <div className="footer__inner">
        <div className="footer__brand">
          <div className="footer__brand-row">
            <Icon name="leaf" size={17} />
            NutriApp
          </div>
          <p className="footer__tagline">
            Gestión interna de recetas digitales con descuento. Uso exclusivo de profesionales
            validados por el administrador.
          </p>
        </div>
        <div className="footer__col">
          <span className="footer__col-title">Plataforma</span>
          <Link className="footer__link" to="/dashboard">
            Panel
          </Link>
          <Link className="footer__link" to="/recetas">
            Recetas
          </Link>
          <Link className="footer__link" to="/pacientes">
            Pacientes
          </Link>
          <Link className="footer__link" to="/cierre-mensual">
            Cierre mensual
          </Link>
        </div>
      </div>
      <div className="footer__bar">
        <div className="footer__bar-inner">
          <span>
            © {new Date().getFullYear()} NutriApp — plataforma interna. Todos los datos son
            confidenciales.
          </span>
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
        </div>
      </div>
    </footer>
  );
}
