// Guard de ruta protegida. Sin sesión → redirige al login. NO redirect PKCE (patrón imedba).
// En modo pre-lanzamiento el login vive en `/ingresar` (en `/` está la landing "Próximamente"),
// así que una sesión vencida no puede rebotar a `/` o el usuario queda sin dónde loguearse.

import { Navigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { config } from "../../config";

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { me, initializing } = useAuth();

  if (initializing) {
    return <div className="app-loading">Cargando…</div>;
  }
  if (!me) {
    return <Navigate to={config.comingSoon ? "/ingresar" : "/"} replace />;
  }
  return <>{children}</>;
}
