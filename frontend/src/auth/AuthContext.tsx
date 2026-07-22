// Contexto de sesión: mantiene el usuario (GET /me), y expone login/logout.
// La ruta protegida usa `me` para saber si hay sesión; el token vive en lib/auth (localStorage).

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "../api/client";
import * as auth from "../lib/auth";
import type { Me } from "../types/session";

interface AuthState {
  me: Me | null;
  /** true mientras se resuelve la sesión inicial (evita parpadeo del login). */
  initializing: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [initializing, setInitializing] = useState(true);

  const loadMe = useCallback(async () => {
    const fresh = await api.get<Me>("/me");
    setMe(fresh);
  }, []);

  // Al montar: si hay token guardado, resolver /me; si falla, sesión inválida.
  useEffect(() => {
    (async () => {
      if (auth.isAuthenticated()) {
        try {
          await loadMe();
        } catch {
          auth.clearSession();
        }
      }
      setInitializing(false);
    })();
  }, [loadMe]);

  const login = useCallback(
    async (username: string, password: string) => {
      await auth.login(username, password);
      await loadMe();
    },
    [loadMe],
  );

  const logout = useCallback(() => {
    auth.clearSession();
    setMe(null);
  }, []);

  return (
    <AuthContext.Provider value={{ me, initializing, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de <AuthProvider>");
  return ctx;
}
