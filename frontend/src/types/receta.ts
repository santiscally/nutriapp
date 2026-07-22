// Espejo del contrato de recetas (POST/GET /recetas). Shape verificado contra el backend seeded.
import type { Paciente } from "./paciente";
import type { Producto } from "./producto";

export type EstadoReceta = "PENDIENTE" | "APLICADA" | "VENCIDA" | "ANULADA";

// --- Request de emisión ---
export interface RecetaItemInput {
  productoId: string;
  cantidad: number;
  indicaciones?: string;
}

export interface RecetaCreateRequest {
  pacienteId: string;
  items: RecetaItemInput[];
  descuentoPct?: number; // opcional: default del parámetro de config del back
}

// --- Response ---
export interface RecetaItem {
  producto: Producto;
  cantidad: number;
  precioLista: number;
  indicaciones?: string;
}

export interface RecetaNotificacion {
  canal: "EMAIL" | "WHATSAPP";
  estado: string; // QUEUED | SENT | FAILED
  sentAt: string | null;
}

export interface RecetaConversion {
  ordenNumero: number;
  ordenTotal: number;
  paidAt: string;
  comisionPct: number;
  comisionMonto: number;
}

export interface RecetaResponse {
  id: string;
  codigo: string;
  estado: EstadoReceta;
  paciente: Paciente;
  items: RecetaItem[];
  descuentoPct: number;
  emitidaAt: string;
  venceAt: string;
  cuponSyncEstado: string; // PENDIENTE mientras la integración esté en stub
  notificaciones?: RecetaNotificacion[];
  conversion?: RecetaConversion | null;
}

// Query params de GET /recetas.
export interface RecetaQuery {
  estado?: EstadoReceta | "";
  pacienteId?: string;
  desde?: string; // YYYY-MM-DD
  hasta?: string; // YYYY-MM-DD
  q?: string; // código o nombre de paciente
  page?: number;
  size?: number;
}
