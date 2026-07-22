import type { RegistroRequest, RegistroResponse } from "../types/registro";
import { api } from "./client";

/** Alta pública de nutricionista (sin sesión). El client no adjunta Bearer si no hay token. */
export const registrar = (body: RegistroRequest) =>
  api.post<RegistroResponse>("/registro", body);
