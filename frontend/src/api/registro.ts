import type { RegistroRequest, RegistroResponse } from "../types/registro";
import { api } from "./client";

/**
 * Alta pública de nutricionista (sin sesión). Va como multipart (C-08): los datos en la parte
 * `datos` y el PDF/foto de la matrícula en `matricula`.
 */
export const registrar = (body: RegistroRequest, matricula: File) => {
  const fd = new FormData();
  // El Blob con type JSON es lo que hace que Spring bindee la parte a @RequestPart @Valid.
  fd.append("datos", new Blob([JSON.stringify(body)], { type: "application/json" }));
  fd.append("matricula", matricula);
  return api.post<RegistroResponse>("/registro", fd);
};
