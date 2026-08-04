// Layout autenticado (rediseño 2026-07-26): top NavBar (marca + navegación + CTA + usuario) +
// contenido centrado (<Outlet/>) + Footer. Reemplaza el sidebar/topbar anterior.

import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { Avatar } from "../ui/Avatar";
import { Icon } from "../ui/Icon";
import { Footer } from "./Footer";

// C-07 (call 56:17): el admin NO emite recetas — Gon fue explícito en que si quieren recetar se
// crean su propia cuenta de nutricionista. No es sólo esconder ítems: el rol ADMIN en Keycloak ya
// no arrastra recetas:*/pacientes:*/dashboard:read, así que esos endpoints le dan 403.
const NAV_NUTRI: { to: string; label: string; end?: boolean }[] = [
  { to: "/dashboard", label: "Panel" },
  { to: "/recetas", label: "Recetas", end: true },
  { to: "/pacientes", label: "Pacientes" },
  { to: "/cierre-mensual", label: "Cierre mensual" },
  { to: "/perfil", label: "Mi perfil" },
];

const NAV_ADMIN: { to: string; label: string; end?: boolean }[] = [
  { to: "/nutricionistas", label: "Nutricionistas" },
  { to: "/cierres", label: "Cierres" },
  { to: "/catalogo", label: "Productos" },
  { to: "/integraciones", label: "Integraciones" },
];

const roleLabel = (roles?: string[]) =>
  roles?.includes("ADMIN") ? "Administrador" : "Nutricionista";

export function AppLayout() {
  const { me, logout } = useAuth();
  const isAdmin = me?.roles.includes("ADMIN") ?? false;
  const nav = isAdmin ? NAV_ADMIN : NAV_NUTRI;

  return (
    <div className="app-shell">
      <header className="navbar">
        <div className="navbar__inner">
          <Link to={isAdmin ? "/nutricionistas" : "/dashboard"} className="navbar__brand">
            <span className="navbar__brand-badge">
              <Icon name="leaf" />
            </span>
            NutriApp
          </Link>

          <nav className="navbar__nav">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  "navbar__link" + (isActive ? " navbar__link--active" : "")
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="navbar__right">
            {!isAdmin && (
              <Link to="/recetas/nueva" className="navbar__cta">
                <Icon name="plus" size={17} />
                Nueva receta
              </Link>
            )}
            <div className="navbar__divider" />
            <div className="navbar__user">
              <Avatar me={me} />
              <span className="navbar__user-meta">
                <span className="navbar__user-name">
                  {me ? `${me.nombre} ${me.apellido}` : ""}
                </span>
                <span className="navbar__user-role">{roleLabel(me?.roles)}</span>
              </span>
            </div>
            <button
              className="navbar__logout"
              onClick={logout}
              title="Salir"
              aria-label="Salir"
            >
              <Icon name="logout" size={17} />
            </button>
          </div>
        </div>
      </header>

      <main className="content">
        <Outlet />
      </main>

      <Footer />
    </div>
  );
}
