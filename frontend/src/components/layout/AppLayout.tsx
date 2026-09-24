// Layout autenticado (rediseño 2026-07-26): top NavBar (marca + navegación + CTA + usuario) +
// contenido centrado (<Outlet/>) + Footer. Reemplaza el sidebar/topbar anterior.

import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { Avatar } from "../ui/Avatar";
import { Icon } from "../ui/Icon";
import { Logo } from "../ui/Logo";
import { Footer } from "./Footer";

// C-07 (call 56:17): el admin NO emite recetas — Gon fue explícito en que si quieren recetar se
// crean su propia cuenta de nutricionista. No es sólo esconder ítems: el rol ADMIN en Keycloak ya
// no arrastra recetas:*/pacientes:*/dashboard:read, así que esos endpoints le dan 403.
const NAV_NUTRI: { to: string; label: string; end?: boolean }[] = [
  { to: "/dashboard", label: "Panel" },
  { to: "/bonos", label: "Bonos", end: true },
  { to: "/pacientes", label: "Pacientes" },
  { to: "/cierre-mensual", label: "Cierre mensual" },
  { to: "/perfil", label: "Mi perfil" },
];

const NAV_ADMIN: { to: string; label: string; end?: boolean }[] = [
  // F-24/F-25 — el panel va primero porque pasó a ser la home del admin, y los bonos al lado de
  // los profesionales: son las dos vistas que el cliente pidió para mirar la operación.
  { to: "/panel", label: "Panel" },
  { to: "/admin/bonos", label: "Bonos" },
  { to: "/profesionales", label: "Profesionales" },
  { to: "/cierres", label: "Cierres" },
  { to: "/catalogo", label: "Productos" },
  { to: "/integraciones", label: "Integraciones" },
];

const roleLabel = (roles?: string[]) =>
  roles?.includes("ADMIN") ? "Administrador" : "Profesional";

export function AppLayout() {
  const { me, logout } = useAuth();
  const isAdmin = me?.roles.includes("ADMIN") ?? false;
  const nav = isAdmin ? NAV_ADMIN : NAV_NUTRI;
  // Estando ya en el emisor, el CTA "Nuevo bono" no lleva a ningún lado: se esconde.
  const enEmision = useLocation().pathname === "/bonos/nuevo";
  // En celular la navegación va plegada detrás de un botón; en escritorio no se usa.
  const [menuAbierto, setMenuAbierto] = useState(false);
  const botonMenu = useRef<HTMLButtonElement>(null);
  const cerrarMenu = () => setMenuAbierto(false);

  useEffect(() => {
    if (!menuAbierto) return;
    const alEscape = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      setMenuAbierto(false);
      // El menú se oculta con el foco adentro: sin esto el foco cae al body y se pierde el lugar.
      botonMenu.current?.focus();
    };
    window.addEventListener("keydown", alEscape);
    return () => window.removeEventListener("keydown", alEscape);
  }, [menuAbierto]);

  useEffect(() => {
    // Abierto en celular y agrandado a escritorio, al volver a achicar reaparecía abierto.
    const escritorio = window.matchMedia("(min-width: 721px)");
    const alCambiar = (e: MediaQueryListEvent) => {
      if (e.matches) setMenuAbierto(false);
    };
    escritorio.addEventListener("change", alCambiar);
    return () => escritorio.removeEventListener("change", alCambiar);
  }, []);

  return (
    <div className="app-shell">
      <header className={"navbar" + (menuAbierto ? " navbar--abierto" : "")}>
        <div className="navbar__inner">
          <Link to={isAdmin ? "/panel" : "/dashboard"} className="navbar__brand" onClick={cerrarMenu}>
            <Logo size={34} />
            <span className="navbar__brand-name">BonosApp</span>
          </Link>

          <nav id="navbar-menu" className="navbar__nav" onClick={cerrarMenu}>
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
            {/* La cuenta y el botón de salir viven arriba a la derecha; en el menú de celular bajan acá. */}
            <div className="navbar__cuenta">
              <Avatar me={me} />
              <span className="navbar__cuenta-meta">
                <span className="navbar__user-name">
                  {me ? `${me.nombre} ${me.apellido}` : ""}
                </span>
                <span className="navbar__user-role">{roleLabel(me?.roles)}</span>
              </span>
              <button type="button" className="btn btn--sm btn--ghost" onClick={logout}>
                <Icon name="logout" size={16} />
                Salir
              </button>
            </div>
          </nav>

          <div className="navbar__right">
            {!isAdmin && !enEmision && (
              <Link to="/bonos/nuevo" className="navbar__cta" onClick={cerrarMenu}>
                <Icon name="plus" size={17} />
                Nuevo bono
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
            <button
              ref={botonMenu}
              type="button"
              className="navbar__menu"
              aria-controls="navbar-menu"
              aria-expanded={menuAbierto}
              aria-label={menuAbierto ? "Cerrar el menú" : "Abrir el menú"}
              onClick={() => setMenuAbierto((v) => !v)}
            >
              <Icon name={menuAbierto ? "close" : "menu"} size={20} />
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
