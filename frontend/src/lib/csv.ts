// Exportable a Excel (C-06, call 57:02: "un clic y que te tire un Excel").
//
// Dos decisiones que importan para que Excel en español lo abra bien de una:
//  - separador `;` — con locale es-AR, Excel espera punto y coma; con `,` mete todo en una columna.
//  - BOM UTF-8 al principio — sin él, Excel rompe los acentos y las ñ.

const SEP = ";";

function celda(v: unknown): string {
  if (v === null || v === undefined) return "";
  const s = String(v);
  // Números con coma decimal, que es lo que espera Excel en es-AR.
  const texto = typeof v === "number" ? s.replace(".", ",") : s;
  return /[";\n]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
}

export function generarCsv(headers: string[], filas: unknown[][]): string {
  return [headers, ...filas].map((f) => f.map(celda).join(SEP)).join("\r\n");
}

/** Dispara la descarga en el browser. El contenido ya lo tenemos: no pega de nuevo al backend. */
export function descargarCsv(nombreArchivo: string, contenido: string): void {
  const blob = new Blob(["﻿" + contenido], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nombreArchivo;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
