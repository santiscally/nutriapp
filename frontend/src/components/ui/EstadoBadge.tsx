// Badge de estado de receta. Reutilizado por Dashboard y Recetas.
// El label sale de `estadoLabel` (F-12: masculino en la UI, enum intacto en el backend).
import { estadoLabel } from "../../lib/format";
import type { EstadoReceta } from "../../types/receta";

export function EstadoBadge({ estado }: { estado: EstadoReceta | string }) {
  return <span className={`badge badge--${estado.toLowerCase()}`}>{estadoLabel(estado)}</span>;
}
