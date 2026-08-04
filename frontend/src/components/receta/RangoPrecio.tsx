// Slider de rango de precio: dos pulgares sobre una misma barra, con el mínimo y el máximo reales
// del catálogo en los extremos.
//
// Está hecho con dos <input type="range"> superpuestos en vez de una librería: el proyecto no tiene
// dependencias de UI y no vale la pena sumar uno por un control. El truco es que la barra de arriba
// tiene `pointer-events: none` salvo en los pulgares, así el click cae en el que corresponde.
//
// Escala logarítmica: el catálogo va de ~$100 a cientos de miles, con la mayoría abajo. En escala
// lineal, todo lo que se usa vive en el primer 5% del recorrido y el control es inutilizable.

import { useId } from "react";

interface Props {
  min: number;
  max: number;
  desde: number;
  hasta: number;
  onChange: (desde: number, hasta: number) => void;
  formato: (n: number) => string;
}

const PASOS = 100;

/** posición del slider (0..PASOS) → precio */
function aPrecio(pos: number, min: number, max: number): number {
  if (max <= min) return min;
  const t = pos / PASOS;
  const valor = Math.exp(Math.log(min || 1) + t * (Math.log(max) - Math.log(min || 1)));
  return Math.round(valor);
}

/** precio → posición del slider (0..PASOS) */
function aPos(precio: number, min: number, max: number): number {
  if (max <= min) return 0;
  const t = (Math.log(Math.max(precio, min || 1)) - Math.log(min || 1)) / (Math.log(max) - Math.log(min || 1));
  return Math.round(Math.min(1, Math.max(0, t)) * PASOS);
}

export function RangoPrecio({ min, max, desde, hasta, onChange, formato }: Props) {
  const id = useId();
  const posDesde = aPos(desde, min, max);
  const posHasta = aPos(hasta, min, max);

  // Porcentajes del tramo activo, para pintar la barra entre los dos pulgares.
  const izq = (posDesde / PASOS) * 100;
  const der = (posHasta / PASOS) * 100;

  return (
    <div className="rango">
      <div className="rango__valores">
        <span>{formato(desde)}</span>
        <span className="muted">Precio</span>
        <span>{formato(hasta)}</span>
      </div>

      <div className="rango__pista">
        <span className="rango__activo" style={{ left: `${izq}%`, right: `${100 - der}%` }} />
        <input
          id={`${id}-desde`}
          className="rango__input"
          type="range"
          min={0}
          max={PASOS}
          value={posDesde}
          aria-label="Precio mínimo"
          onChange={(e) => {
            const pos = Number(e.target.value);
            // Los pulgares no se cruzan: el mínimo empuja al máximo, no lo atraviesa.
            const nuevo = aPrecio(Math.min(pos, posHasta), min, max);
            onChange(nuevo, hasta);
          }}
        />
        <input
          id={`${id}-hasta`}
          className="rango__input"
          type="range"
          min={0}
          max={PASOS}
          value={posHasta}
          aria-label="Precio máximo"
          onChange={(e) => {
            const pos = Number(e.target.value);
            const nuevo = aPrecio(Math.max(pos, posDesde), min, max);
            onChange(desde, nuevo);
          }}
        />
      </div>
    </div>
  );
}
