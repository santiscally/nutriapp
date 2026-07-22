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
