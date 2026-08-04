// POST /registro (público). Alta de nutricionista → queda PENDIENTE hasta que un ADMIN lo apruebe.
import type { EstadoValidacion } from "./session";

/** Lista cerrada de condición fiscal (C-08). Gon corrigió: se pide CUIT, no CUIL. */
export const CONDICIONES_FISCALES = [
  "Responsable Inscripto",
  "Monotributo",
  "Exento",
  "Consumidor Final",
] as const;
export type CondicionFiscal = (typeof CONDICIONES_FISCALES)[number];

export interface RegistroRequest {
  nombre: string;
  apellido: string;
  email: string;
  telefono: string; // E.164
  matricula: string;
  dni: string;
  cuit: string;
  condicionFiscal: string;
  password: string;
}

export interface RegistroResponse {
  id: string;
  estadoValidacion: EstadoValidacion; // PENDIENTE al crearse
}
