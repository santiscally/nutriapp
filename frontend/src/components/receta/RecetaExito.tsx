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
        <h1>Bono emitido</h1>
        <p className="muted">
          {receta.paciente.nombre} {receta.paciente.apellido} recibe el código por mail.
          {receta.waMeUrl && " Mandáselo también por WhatsApp desde el botón de abajo."}
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
                {it.cantidad > 1 ? `${it.cantidad}× ` : ""}
                {it.producto.nombre}
              </span>
            </li>
          ))}
        </ul>
        <div className="exito__actions">
          {/* 2.4: el WhatsApp lo manda la nutricionista, no el sistema — por eso es la acción
              principal de esta pantalla y no una opción escondida en el detalle. */}
          {receta.waMeUrl && (
            <a
              className="btn btn--whatsapp"
              href={receta.waMeUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              <Icon name="send" size={16} />
              Enviar por WhatsApp
            </a>
          )}
          <button className="btn btn--ghost" onClick={onNueva}>
            Emitir otro bono
          </button>
          <Link className="btn btn--ghost" to="/recetas">
            Ver todos los bonos
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
          El <strong style={{ color: "var(--text)" }}>mail</strong> sale solo (podés reenviarlo desde
          el detalle del bono). El <strong style={{ color: "var(--text)" }}>WhatsApp</strong> lo
          mandás vos: el botón abre el chat con el paciente y el mensaje ya escrito.
        </p>
      </div>
    </section>
  );
}
