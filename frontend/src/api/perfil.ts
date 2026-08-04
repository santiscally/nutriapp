import { api } from "./client";

/** C-17 — sube/reemplaza la foto de perfil. Devuelve el data URI ya redimensionado. */
export const subirFoto = (foto: File) => {
  const fd = new FormData();
  fd.append("foto", foto);
  return api.post<{ foto: string }>("/perfil/foto", fd);
};

export const borrarFoto = () => api.del<void>("/perfil/foto");

/**
 * Cambia la propia contraseña. Pide la actual: el backend la verifica contra Keycloak antes de
 * pisarla, así una sesión abierta y olvidada no alcanza para quedarse con la cuenta.
 */
export const cambiarPassword = (passwordActual: string, passwordNueva: string) =>
  api.put<void>("/perfil/password", { passwordActual, passwordNueva });
