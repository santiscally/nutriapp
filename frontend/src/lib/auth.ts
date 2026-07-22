// Auth por ROPC (Resource Owner Password Credentials) contra Keycloak.
// Patrón portado de imedba: login directo usuario/contraseña contra el client público
// `nutriapp-frontend` (Direct Access Grants habilitado), sin redirect PKCE.
// Los tokens se guardan en localStorage; el client.ts los inyecta como Bearer y refresca ante 401.

import { config } from "../config";

const STORAGE_KEY = "nutriapp.tokens";

interface StoredTokens {
  accessToken: string;
  refreshToken: string;
  /** epoch ms en el que expira el access token (con margen). */
  expiresAt: number;
}

interface KeycloakTokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  error?: string;
  error_description?: string;
}

const tokenEndpoint = () =>
  `${config.keycloak.url}/realms/${config.keycloak.realm}/protocol/openid-connect/token`;

function store(t: KeycloakTokenResponse): StoredTokens {
  const tokens: StoredTokens = {
    accessToken: t.access_token,
    refreshToken: t.refresh_token,
    // margen de 15s para no usar un token a punto de expirar
    expiresAt: Date.now() + (t.expires_in - 15) * 1000,
  };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  return tokens;
}

function read(): StoredTokens | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredTokens;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEY);
}

export function isAuthenticated(): boolean {
  return read() !== null;
}

/** Login ROPC. Lanza Error con mensaje legible si las credenciales fallan. */
export async function login(username: string, password: string): Promise<void> {
  const body = new URLSearchParams({
    grant_type: "password",
    client_id: config.keycloak.clientId,
    username,
    password,
  });

  const res = await fetch(tokenEndpoint(), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  const data = (await res.json()) as KeycloakTokenResponse;
  if (!res.ok) {
    // Keycloak devuelve error_description tipo "Invalid user credentials"
    throw new Error(
      data.error_description === "Invalid user credentials"
        ? "Usuario o contraseña incorrectos."
        : data.error_description ?? "No se pudo iniciar sesión.",
    );
  }
  store(data);
}

/** Intenta refrescar el access token con el refresh token guardado. */
async function refresh(): Promise<StoredTokens | null> {
  const current = read();
  if (!current) return null;

  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: config.keycloak.clientId,
    refresh_token: current.refreshToken,
  });

  const res = await fetch(tokenEndpoint(), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  if (!res.ok) {
    clearSession();
    return null;
  }
  return store((await res.json()) as KeycloakTokenResponse);
}

/** Devuelve un access token válido (refrescando si hace falta) o null si no hay sesión. */
export async function getValidAccessToken(): Promise<string | null> {
  const current = read();
  if (!current) return null;
  if (Date.now() < current.expiresAt) return current.accessToken;
  const refreshed = await refresh();
  return refreshed?.accessToken ?? null;
}
