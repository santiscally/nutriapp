// Modal minimal: overlay + card. Cierra al click en el backdrop o con Escape.
//
// El alto está acotado al viewport y el que scrollea es el cuerpo, no la card: con contenido largo
// (un producto con descripción y 20 tags) la card crecía hasta cortarse contra el borde de la
// pantalla y el botón de cerrar quedaba afuera. El header queda siempre visible.

import { useEffect, type ReactNode } from "react";

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
  /** Para contenido de dos columnas (ficha del admin, detalle de producto con imagen). */
  ancho?: boolean;
}

export function Modal({ title, onClose, children, ancho = false }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="modal__backdrop" onClick={onClose}>
      <div
        className={"card modal__card" + (ancho ? " modal__card--ancho" : "")}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal__header">
          <h2 className="modal__title">{title}</h2>
          <button className="modal__close" onClick={onClose} aria-label="Cerrar">
            ×
          </button>
        </div>
        <div className="modal__body">{children}</div>
      </div>
    </div>
  );
}
