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

/**
 * F-05 — Jurisdicción de matrícula: lista cerrada (23 provincias + CABA) en vez de texto libre.
 * El backend sigue recibiendo `matricula` como "<jurisdicción> · N° <número>", así que esto es
 * sólo del front.
 */
export const JURISDICCIONES = [
  "Ciudad Autónoma de Buenos Aires",
  "Buenos Aires",
  "Catamarca",
  "Chaco",
  "Chubut",
  "Córdoba",
  "Corrientes",
  "Entre Ríos",
  "Formosa",
  "Jujuy",
  "La Pampa",
  "La Rioja",
  "Mendoza",
  "Misiones",
  "Neuquén",
  "Río Negro",
  "Salta",
  "San Juan",
  "San Luis",
  "Santa Cruz",
  "Santa Fe",
  "Santiago del Estero",
  "Tierra del Fuego",
  "Tucumán",
] as const;
export type Jurisdiccion = (typeof JURISDICCIONES)[number];
