// Cliente HTTP: fetch nativo envuelto (sin axios — decisión del proyecto).
// - Inyecta el Bearer token en cada request.
// - Parsea el ApiError del backend y surfacea `message` (lección imedba: nunca mostrar "HTTP 409" pelado).
// - Ante 401 limpia la sesión para forzar re-login.

import { config } from "../config";
import { clearSession, getValidAccessToken } from "../lib/auth";
import type { ApiError } from "../types/common";

/** Error de aplicación con el mensaje ya legible para mostrar al usuario. */
export class ApiRequestError extends Error {
  status: number;
  fieldErrors?: { field?: string; message: string }[];
  constructor(status: number, message: string, fieldErrors?: ApiError["errors"]) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** query params; se serializan omitiendo undefined/null/"" */
  params?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
}

function buildUrl(path: string, params?: RequestOptions["params"]): string {
  const url = new URL(`${config.apiBaseUrl}${path}`);
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const token = await getValidAccessToken();
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;

  const hasBody = opts.body !== undefined;
  if (hasBody) headers["Content-Type"] = "application/json";

  const res = await fetch(buildUrl(path, opts.params), {
    method: opts.method ?? "GET",
    headers,
    body: hasBody ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
  });

  if (res.status === 401) {
    clearSession();
    throw new ApiRequestError(401, "Tu sesión expiró. Iniciá sesión de nuevo.");
  }

  if (res.status === 204) return undefined as T;

  const isJson = res.headers.get("content-type")?.includes("application/json");
  const payload = isJson ? await res.json() : await res.text();

  if (!res.ok) {
    const err = (isJson ? payload : null) as ApiError | null;
    throw new ApiRequestError(
      res.status,
      err?.message ?? `Error ${res.status}`,
      err?.errors,
    );
  }

  return payload as T;
}

export const api = {
  get: <T>(path: string, params?: RequestOptions["params"], signal?: AbortSignal) =>
    request<T>(path, { method: "GET", params, signal }),
  post: <T>(path: string, body?: unknown, signal?: AbortSignal) =>
    request<T>(path, { method: "POST", body, signal }),
  put: <T>(path: string, body?: unknown, signal?: AbortSignal) =>
    request<T>(path, { method: "PUT", body, signal }),
  del: <T>(path: string, signal?: AbortSignal) =>
    request<T>(path, { method: "DELETE", signal }),
};
