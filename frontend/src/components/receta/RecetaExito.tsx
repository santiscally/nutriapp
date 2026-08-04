// Pantalla de éxito tras emitir: código de cupón + descuento/vigencia + estado de sync.
// C-02: sin importes — una vez emitida, la receta no muestra precios en ningún lado.

import { Link } from "react-router-dom";
import { fecha } from "../../lib/format";
import { Icon } from "../ui/Icon";
import type { RecetaResponse } from "../../types/receta";

export function RecetaExito({ receta, onNueva }: { receta: RecetaResponse; onNueva: () => void }) {
  return (
    <section className="exito">
      <div className="card exito__card">
        <div className="exito__check">
          <Icon name="check-circle" size={28} />
        </div>
        <h1>Receta emitida</h1>
        <p className="muted">
          {receta.paciente.nombre} {receta.paciente.apellido} recibe el código por mail y WhatsApp.
        </p>

        <div className="exito__codigo">
          <span className="muted">Código de descuento</span>
          <strong className="mono">{receta.codigo}</strong>
          <span className="exito__codigo-sub">
            {receta.descuentoPct}% de descuento · válido hasta el {fecha(receta.venceAt)}
          </span>
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
            </li>
          ))}
        </ul>
        <div className="exito__actions">
          <button className="btn btn--primary" onClick={onNueva}>
            Emitir otra receta
          </button>
          <Link className="btn btn--ghost" to="/recetas">
            Ver todas las recetas
          </Link>
        </div>
      </div>

      <div
        className="card"
        style={{
          maxWidth: 480,
          width: "100%",
          marginTop: "1.1rem",
          display: "flex",
          gap: "0.7rem",
          alignItems: "flex-start",
          textAlign: "left",
        }}
      >
        <span style={{ color: "var(--primary)", flex: "none", marginTop: "2px" }}>
          <Icon name="send" size={18} />
        </span>
        <p className="muted" style={{ margin: 0 }}>
          El código se envía al paciente por <strong style={{ color: "var(--text)" }}>mail</strong> y{" "}
          <strong style={{ color: "var(--text)" }}>WhatsApp</strong>. Podés reenviarlas desde el
          detalle de la receta.
        </p>
      </div>
    </section>
  );
}
