// Pantalla de éxito tras emitir: muestra el código de cupón + estado de sync/notificaciones.

import { Link } from "react-router-dom";
import { fecha, money } from "../../lib/format";
import type { RecetaResponse } from "../../types/receta";

export function RecetaExito({ receta, onNueva }: { receta: RecetaResponse; onNueva: () => void }) {
  return (
    <section className="exito">
      <div className="card exito__card">
        <div className="exito__check">✓</div>
        <h1>Receta emitida</h1>
        <p className="muted">
          Para {receta.paciente.nombre} {receta.paciente.apellido} · vence el {fecha(receta.venceAt)}
        </p>

        <div className="exito__codigo">
          <span className="muted">Código de descuento</span>
          <strong className="mono">{receta.codigo}</strong>
        </div>

        {receta.cuponSyncEstado === "PENDIENTE" && (
          <div className="alert alert--info">
            El cupón quedó <strong>pendiente de sincronizar</strong> con la tienda (integración en modo
            stub). Se registrará automáticamente cuando se conecte TiendaNube.
          </div>
        )}

        <ul className="exito__items">
          {receta.items.map((it, i) => (
            <li key={i}>
              <span>
                {it.cantidad}× {it.producto.nombre}
              </span>
              <span className="mono">{money(it.precioLista * it.cantidad)}</span>
            </li>
          ))}
        </ul>
        <p className="exito__descuento">Descuento aplicado: {receta.descuentoPct}%</p>

        <div className="exito__actions">
          <button className="btn btn--primary" onClick={onNueva}>
            Emitir otra
          </button>
          <Link className="btn btn--ghost" to="/recetas">
            Ver recetas
          </Link>
        </div>
      </div>
    </section>
  );
}
