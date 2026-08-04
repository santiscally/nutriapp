// Avatar: la foto de perfil (C-17) o, si no cargó ninguna, las iniciales.
// La foto viene embebida en /me como data URI, así que no hay request extra ni object URLs.

import type { Me } from "../../types/session";

const iniciales = (nombre?: string, apellido?: string) =>
  `${nombre?.[0] ?? ""}${apellido?.[0] ?? ""}`.toUpperCase() || "·";

export function Avatar({ me, size = 36 }: { me: Me | null; size?: number }) {
  const estilo = { width: size, height: size, fontSize: Math.round(size * 0.32) };

  if (me?.foto) {
    return (
      <img
        className="avatar avatar--img"
        src={me.foto}
        alt={`Foto de ${me.nombre ?? ""}`}
        style={estilo}
      />
    );
  }
  return (
    <span className="avatar" style={estilo}>
      {iniciales(me?.nombre, me?.apellido)}
    </span>
  );
}
