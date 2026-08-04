// C-09 — bandeja de nutricionistas (solo admin). Dos tabs: solicitudes pendientes / aceptadas
// (call 39:26). Desde la ficha se aprueba, se rechaza y se setean los % propios (C-01).

import { useCallback, useState } from "react";
import { listarNutricionistas } from "../api/nutricionistas";
import { ParametrosModal } from "../components/nutricionista/ParametrosModal";
import { EmptyState } from "../components/ui/EmptyState";
import { TableSkeleton } from "../components/ui/Skeleton";
import { useFetch } from "../hooks/useFetch";
import { fecha } from "../lib/format";
import type { EstadoValidacion, NutricionistaAdmin } from "../types/nutricionista";

const TABS: { estado: EstadoValidacion; label: string }[] = [
  { estado: "PENDIENTE", label: "Solicitudes pendientes" },
  { estado: "APROBADA", label: "Aceptadas" },
  { estado: "RECHAZADA", label: "Rechazadas" },
];

/** Estado de acceso: separado del estado de la solicitud (una aprobada puede estar dada de baja). */
function AccesoBadge({ activo }: { activo: boolean }) {
  return activo ? (
    <span className="badge badge--ok">Activo</span>
  ) : (
    <span className="badge badge--off">Sin acceso</span>
  );
}

export function Nutricionistas() {
  const [tab, setTab] = useState<EstadoValidacion>("PENDIENTE");
  const [q, setQ] = useState("");
  const [sel, setSel] = useState<NutricionistaAdmin | null>(null);

  const fetcher = useCallback(
    (signal: AbortSignal) => listarNutricionistas({ estado: tab, q }, signal),
    [tab, q],
  );
  const { data, loading, error, refetch } = useFetch(fetcher, [tab, q]);

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Nutricionistas</h1>
          <p className="muted">
            Validá las solicitudes y definí el descuento y la comisión de cada una.
          </p>
        </div>
      </div>

      <div className="filtros card">
        <div className="tabs">
          {TABS.map((t) => (
            <button
              key={t.estado}
              type="button"
              className={"tab" + (tab === t.estado ? " tab--active" : "")}
              onClick={() => setTab(t.estado)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <input
          className="picker__input"
          placeholder="Buscar por nombre o email…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      {loading && <TableSkeleton rows={5} cols={5} />}
      {error && <div className="alert alert--error">{error}</div>}

      {data && data.content.length === 0 && (
        <EmptyState
          icon="users"
          title={tab === "PENDIENTE" ? "No hay solicitudes pendientes" : "No hay nutricionistas acá"}
          hint={
            tab === "PENDIENTE"
              ? "Cuando alguien se registre, va a aparecer en esta lista para que la valides."
              : undefined
          }
        />
      )}

      {data && data.content.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Email</th>
              <th>Matrícula</th>
              <th>Descuento</th>
              <th>Comisión</th>
              <th>Acceso</th>
              <th>{tab === "PENDIENTE" ? "Solicitó" : "Validada"}</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((n) => (
              <tr key={n.id} className="row-click" onClick={() => setSel(n)}>
                <td>
                  {n.nombre} {n.apellido}
                </td>
                <td className="muted">{n.email}</td>
                <td className="muted">{n.matricula || "—"}</td>
                <td>
                  <strong>{n.descuentoPct}%</strong>
                </td>
                <td>
                  <strong>{n.comisionPct}%</strong>
                </td>
                <td>
                  <AccesoBadge activo={n.activo} />
                </td>
                <td className="muted">
                  {tab === "PENDIENTE"
                    ? fecha(n.createdAt)
                    : n.validadoAt
                      ? fecha(n.validadoAt)
                      : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {sel && (
        <ParametrosModal
          nutri={sel}
          onClose={() => setSel(null)}
          onChanged={refetch}
        />
      )}
    </section>
  );
}
