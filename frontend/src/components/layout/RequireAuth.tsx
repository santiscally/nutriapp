// Guard de ruta protegida. Sin sesión → redirige a "/" (login). NO redirect PKCE (patrón imedba).

import { Navigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { me, initializing } = useAuth();

  if (initializing) {
    return <div className="app-loading">Cargando…</div>;
  }
  if (!me) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
