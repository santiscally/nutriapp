// Layout autenticado: Sidebar (navegación) + Topbar (usuario + logout) + <Outlet/> para las páginas.

import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { Icon, type IconName } from "../ui/Icon";

const NAV: { to: string; label: string; icon: IconName }[] = [
  { to: "/dashboard", label: "Dashboard", icon: "grid" },
  { to: "/recetas/nueva", label: "Emitir receta", icon: "file-plus" },
  { to: "/recetas", label: "Recetas", icon: "clipboard" },
  { to: "/pacientes", label: "Pacientes", icon: "users" },
];

const initials = (nombre?: string, apellido?: string) =>
  `${nombre?.[0] ?? ""}${apellido?.[0] ?? ""}`.toUpperCase() || "·";

export function AppLayout() {
  const { me, logout } = useAuth();

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <Icon name="leaf" />
          NutriApp
        </div>
        <nav className="sidebar__nav">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/recetas"}
              className={({ isActive }) =>
                "sidebar__link" + (isActive ? " sidebar__link--active" : "")
              }
            >
              <Icon name={item.icon} />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="topbar__user">
            <span className="avatar">{initials(me?.nombre, me?.apellido)}</span>
            {me ? `${me.nombre} ${me.apellido}` : ""}
          </div>
          <button className="btn btn--ghost" onClick={logout}>
            <Icon name="logout" />
            Salir
          </button>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
