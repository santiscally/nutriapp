import { api } from "./client";

/**
 * Pide el mail de recupero (S-09). Público, sin sesión.
 *
 * Responde 204 exista o no la cuenta: el backend no delata qué mails están registrados, así que
 * la pantalla muestra siempre el mismo mensaje.
 */
export const pedirRecuperoPassword = (email: string) =>
  api.post<void>("/password/recuperar", { email });
