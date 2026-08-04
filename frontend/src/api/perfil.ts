import { api } from "./client";

/** C-17 — sube/reemplaza la foto de perfil. Devuelve el data URI ya redimensionado. */
export const subirFoto = (foto: File) => {
  const fd = new FormData();
  fd.append("foto", foto);
  return api.post<{ foto: string }>("/perfil/foto", fd);
};

export const borrarFoto = () => api.del<void>("/perfil/foto");
