// C-07 — guard por rol. El backend ya devuelve 403 (el rol ADMIN perdió recetas:*/pacientes:*/
// dashboard:read), así que esto no es la seguridad: es para que nadie caiga por URL directa en
// una pantalla que le va a tirar errores.

import { Navigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { homeDe } from "../../lib/home";

export function RequireRol({
  rol,
  children,
}: {
  rol: "ADMIN" | "NUTRICIONISTA";
  children: React.ReactNode;
}) {
  const { me } = useAuth();
  const esAdmin = me?.roles.includes("ADMIN") ?? false;
  const permitido = rol === "ADMIN" ? esAdmin : !esAdmin;

  if (!permitido) {
    return <Navigate to={homeDe(me)} replace />;
  }
  return <>{children}</>;
}
