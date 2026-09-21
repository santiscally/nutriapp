import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { AppLayout } from "./components/layout/AppLayout";
import { RequireAuth } from "./components/layout/RequireAuth";
import { RequireRol } from "./components/layout/RequireRol";
import { DialogProvider } from "./components/ui/Dialog";
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
import { Proximamente } from "./pages/Proximamente";
import { Recetas } from "./pages/Recetas";
import { Registro } from "./pages/Registro";
import { config } from "./config";

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <DialogProvider>
        <BrowserRouter>
        <Routes>
          {/* Públicas.
              Modo pre-lanzamiento (VITE_COMING_SOON=true): `/` es la landing "Próximamente" y lo
              único ofrecido es registrarse. El login no desaparece — queda en `/ingresar`, sin
              link, para el equipo y las demos. `/ingresar` existe siempre (en modo normal es
              simplemente un alias de `/`), así los bookmarks no se rompen al apagar el modo. */}
          <Route path="/" element={config.comingSoon ? <Proximamente /> : <Login />} />
          <Route path="/ingresar" element={<Login />} />
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
            <Route path="/bonos/nuevo" element={<RequireRol rol="NUTRICIONISTA"><EmitirReceta /></RequireRol>} />
            <Route path="/bonos" element={<RequireRol rol="NUTRICIONISTA"><Recetas /></RequireRol>} />
            <Route path="/pacientes" element={<RequireRol rol="NUTRICIONISTA"><Pacientes /></RequireRol>} />
            <Route path="/cierre-mensual" element={<RequireRol rol="NUTRICIONISTA"><CierreMensual /></RequireRol>} />
            <Route path="/perfil" element={<RequireRol rol="NUTRICIONISTA"><Perfil /></RequireRol>} />

            {/* Sólo admin. */}
            <Route path="/profesionales" element={<RequireRol rol="ADMIN"><Nutricionistas /></RequireRol>} />
            <Route path="/cierres" element={<RequireRol rol="ADMIN"><CierreConsolidado /></RequireRol>} />
            <Route path="/catalogo" element={<RequireRol rol="ADMIN"><CatalogoAdmin /></RequireRol>} />
            <Route path="/integraciones" element={<RequireRol rol="ADMIN"><Integraciones /></RequireRol>} />
          </Route>

          {/* Rutas viejas (F-08 / F-09): el cliente ya tiene bookmarks de la 1ª entrega, así que
              en vez de caer en el fallback redirigen al nombre nuevo. */}
          <Route path="/recetas/nueva" element={<Navigate to="/bonos/nuevo" replace />} />
          <Route path="/recetas" element={<Navigate to="/bonos" replace />} />
          <Route path="/nutricionistas" element={<Navigate to="/profesionales" replace />} />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </BrowserRouter>
        </DialogProvider>
      </ToastProvider>
    </AuthProvider>
  );
}
