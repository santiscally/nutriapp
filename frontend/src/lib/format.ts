/** Formato de moneda ARS (es-AR). */
export const money = (n: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(n);

/** Fecha corta local (dd/mm/aaaa). */
export const fecha = (iso: string) => new Date(iso).toLocaleDateString("es-AR");

/** Fecha + hora local (dd/mm/aaaa hh:mm). */
export const fechaHora = (iso: string) =>
  new Date(iso).toLocaleString("es-AR", {
    dateStyle: "short",
    timeStyle: "short",
  });

// F-12 — el cliente quiere los estados del bono en masculino ("Aplicado", no "Aplicada"). Es SOLO
// display: el enum del backend y la DB siguen en femenino, acá se traduce la etiqueta visible.
const ESTADO_LABELS: Record<string, string> = {
  PENDIENTE: "Pendiente",
  APLICADA: "Aplicado",
  LIQUIDADA: "Liquidado",
  VENCIDA: "Vencido",
  ANULADA: "Anulado",
};

/** Etiqueta visible de un estado de bono. Si llega algo fuera del enum, se muestra tal cual. */
export const estadoLabel = (estado: string) => ESTADO_LABELS[estado] ?? estado;
