// POST /registro (público). Alta de nutricionista → queda PENDIENTE hasta que un ADMIN lo apruebe.
import type { EstadoValidacion } from "./session";

export interface RegistroRequest {
  nombre: string;
  apellido: string;
  email: string;
  telefono: string; // E.164
  matricula: string;
  password: string;
}

export interface RegistroResponse {
  id: string;
  estadoValidacion: EstadoValidacion; // PENDIENTE al crearse
}
