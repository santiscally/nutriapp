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
  // El % de descuento NO viaja: es el de la nutricionista y lo define el admin (viene en /me).
}

// --- Response ---
// C-02: la receta emitida NO trae precios — sólo producto y cantidad. Los precios se ven
// únicamente en el buscador/carrito de la pantalla de emisión, y son aproximados.
export interface RecetaItem {
  producto: Producto;
  cantidad: number;
  indicaciones?: string;
}

// 2.4: EMAIL es el único canal automático. WhatsApp no se encola: sale por el link wa.me
// (waMeUrl) que la nutricionista abre para mandar el mensaje ella misma.
export interface RecetaNotificacion {
  canal: "EMAIL";
  estado: string; // QUEUED | SENT | FAILED
  sentAt: string | null;
}

// La nutricionista ve lo que gana, no lo que la tienda facturó: el total de la orden no viaja
// (sí está en el cierre consolidado del admin, que es con lo que liquida).
export interface RecetaConversion {
  ordenNumero: number;
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
  // 2.4: link wa.me con el mensaje ya armado, para mandar la receta por WhatsApp a mano.
  // null cuando no corresponde: receta que ya no está PENDIENTE o paciente sin teléfono.
  waMeUrl?: string | null;
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
