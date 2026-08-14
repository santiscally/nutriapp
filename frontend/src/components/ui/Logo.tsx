// Isotipo de la marca (el que pasó Gon). Reemplaza al ícono `leaf` genérico en navbar, footer,
// login, registro y landing. Es multicolor: va sin fondo, nunca dentro de una píldora teñida.

import logoUrl from "../../assets/logo.png";

export function Logo({ size = 34 }: { size?: number }) {
  return (
    <img className="logo" src={logoUrl} alt="" width={size} height={size} aria-hidden="true" />
  );
}
