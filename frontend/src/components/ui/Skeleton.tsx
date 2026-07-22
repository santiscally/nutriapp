// Placeholders de carga con shimmer. Reemplazan el "Cargando…" pelado.

export function Skeleton({ w = "100%", h = "1rem", r = "6px" }: { w?: string; h?: string; r?: string }) {
  return <span className="skeleton" style={{ width: w, height: h, borderRadius: r }} />;
}

/** Esqueleto de tabla: N filas × M columnas, para las listas mientras cargan. */
export function TableSkeleton({ rows = 5, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div className="skeleton-table" aria-hidden="true">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="skeleton-table__row">
          {Array.from({ length: cols }).map((__, c) => (
            <Skeleton key={c} w={c === 0 ? "70%" : "85%"} />
          ))}
        </div>
      ))}
    </div>
  );
}

/** Esqueleto de los 3 tiles del dashboard. */
export function TilesSkeleton() {
  return (
    <div className="tiles" aria-hidden="true">
      {[0, 1, 2].map((i) => (
        <div key={i} className="card tile">
          <Skeleton w="46px" h="46px" r="12px" />
          <span className="tile__body" style={{ flex: 1 }}>
            <Skeleton w="60%" h="0.8rem" />
            <Skeleton w="40%" h="1.4rem" />
          </span>
        </div>
      ))}
    </div>
  );
}
