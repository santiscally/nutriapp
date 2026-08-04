// Espejo del contrato de recetas (POST/GET /recetas). Shape verificado contra el backend seeded.
import type { Paciente } from "./paciente";
import type { Producto } from "./producto";

// LIQUIDADA (C-05): el admin ya le pagó la comisión a la nutricionista. Estado terminal.
export type EstadoReceta = "PENDIENTE" | "APLICADA" | "VENCIDA" | "ANULADA" | "LIQUIDADA";

// --- Request de emisión ---
export interface RecetaItemInput {
  productoId: string;
  cantidad: number;
  indicaciones?: string;
}

export interface RecetaCreateRequest {
  pacienteId: string;
  items: RecetaItemInput[];
  // El % de descuento NO viaja: es fijo global, lo define el admin (GET /configuracion).
}

// --- Response ---
// C-02: la receta emitida NO trae precios — sólo producto y cantidad. Los precios se ven
// únicamente en el buscador/carrito de la pantalla de emisión, y son aproximados.
export interface RecetaItem {
  producto: Producto;
  cantidad: number;
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
  // C-05: cuándo se liquidó (pagó) esta comisión. Ausente/null = convertida pero impaga.
  liquidadaAt?: string | null;
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
