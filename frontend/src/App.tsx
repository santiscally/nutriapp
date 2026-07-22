import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { AppLayout } from "./components/layout/AppLayout";
import { RequireAuth } from "./components/layout/RequireAuth";
import { ToastProvider } from "./components/ui/Toast";
import { Dashboard } from "./pages/Dashboard";
import { EmitirReceta } from "./pages/EmitirReceta";
import { Login } from "./pages/Login";
import { Pacientes } from "./pages/Pacientes";
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
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/recetas/nueva" element={<EmitirReceta />} />
            <Route path="/recetas" element={<Recetas />} />
            <Route path="/pacientes" element={<Pacientes />} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}
