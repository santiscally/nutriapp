// Badge de estado de receta. Reutilizado por Dashboard y Recetas.
import type { EstadoReceta } from "../../types/receta";

export function EstadoBadge({ estado }: { estado: EstadoReceta | string }) {
  return <span className={`badge badge--${estado.toLowerCase()}`}>{estado}</span>;
}
