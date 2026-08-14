// Footer del layout autenticado: una sola barra fija al pie con la marca, el copyright y la
// atribución a Simple Apps.
//
// Antes tenía arriba una columna de links a Panel/Recetas/Pacientes/Cierre. Se sacó por dos
// razones: en el perfil de nutricionista repetía la navegación que ya está en la navbar, y en el
// de admin era directamente inservible — esas cuatro rutas son sólo de nutricionista (C-07), así
// que al admin lo mandaban a un 403 o lo rebotaban al home. La marca con el isotipo sí se conserva.

import { Logo } from "../ui/Logo";

export function Footer() {
  return (
    <footer className="footer">
      <div className="footer__bar-inner">
        <span className="footer__marca">
          <Logo size={20} />
          NutriApp
        </span>
        <span className="footer__copy">
          © {new Date().getFullYear()} — datos confidenciales.
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
    </footer>
  );
}
