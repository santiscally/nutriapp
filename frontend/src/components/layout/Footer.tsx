// Footer del layout autenticado.
//
// La barra de copyright + "powered by Simple Apps" es FIJA al pie de la ventana: es lo que tiene
// que verse siempre, sin depender de que alguien scrollee hasta el final. El bloque de arriba se
// achicó a una sola línea — eran 34px de padding con una bajada de tres renglones y una columna de
// links que ya está en la navbar.

import { Link } from "react-router-dom";
import { Icon } from "../ui/Icon";

export function Footer() {
  return (
    <footer className="footer">
      <div className="footer__inner">
        <span className="footer__brand-row">
          <Icon name="leaf" size={15} />
          NutriApp
        </span>
        <span className="footer__tagline">Uso exclusivo de profesionales validados.</span>
        <nav className="footer__links">
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
        </nav>
      </div>

      <div className="footer__bar">
        <div className="footer__bar-inner">
          <span>© {new Date().getFullYear()} NutriApp — datos confidenciales.</span>
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
