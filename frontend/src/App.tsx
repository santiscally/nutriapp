import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { AppLayout } from "./components/layout/AppLayout";
import { RequireAuth } from "./components/layout/RequireAuth";
import { RequireRol } from "./components/layout/RequireRol";
import { ToastProvider } from "./components/ui/Toast";
import { CierreConsolidado } from "./pages/CierreConsolidado";
import { CierreMensual } from "./pages/CierreMensual";
import { Dashboard } from "./pages/Dashboard";
import { CatalogoAdmin } from "./pages/CatalogoAdmin";
import { EmitirReceta } from "./pages/EmitirReceta";
import { Integraciones } from "./pages/Integraciones";
import { Login } from "./pages/Login";
import { Nutricionistas } from "./pages/Nutricionistas";
import { Pacientes } from "./pages/Pacientes";
import { Perfil } from "./pages/Perfil";
import { Recetas } from "./pages/Recetas";
import { Registro } from "./pages/Registro";

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
        <Routes>
          {/* Públicas */}
          <Route path="/" element={<Login />} />
          <Route path="/registro" element={<Registro />} />

          {/* Protegidas (layout con sidebar/topbar) */}
          <Route
            element={
              <RequireAuth>
                <AppLayout />
              </RequireAuth>
            }
          >
            {/* Sólo nutricionista — el admin no emite recetas (C-07). */}
            <Route path="/dashboard" element={<RequireRol rol="NUTRICIONISTA"><Dashboard /></RequireRol>} />
            <Route path="/recetas/nueva" element={<RequireRol rol="NUTRICIONISTA"><EmitirReceta /></RequireRol>} />
            <Route path="/recetas" element={<RequireRol rol="NUTRICIONISTA"><Recetas /></RequireRol>} />
            <Route path="/pacientes" element={<RequireRol rol="NUTRICIONISTA"><Pacientes /></RequireRol>} />
            <Route path="/cierre-mensual" element={<RequireRol rol="NUTRICIONISTA"><CierreMensual /></RequireRol>} />
            <Route path="/perfil" element={<RequireRol rol="NUTRICIONISTA"><Perfil /></RequireRol>} />

            {/* Sólo admin. */}
            <Route path="/nutricionistas" element={<RequireRol rol="ADMIN"><Nutricionistas /></RequireRol>} />
            <Route path="/cierres" element={<RequireRol rol="ADMIN"><CierreConsolidado /></RequireRol>} />
            <Route path="/catalogo" element={<RequireRol rol="ADMIN"><CatalogoAdmin /></RequireRol>} />
            <Route path="/integraciones" element={<RequireRol rol="ADMIN"><Integraciones /></RequireRol>} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}
