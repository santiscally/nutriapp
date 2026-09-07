// F.4 — Pacientes. Lista con búsqueda + paginación + alta/edición (modal) + baja (soft delete).
// La baja maneja el 409 "tiene recetas PENDIENTES" surfaceando el message del backend.

import { useCallback, useState } from "react";
import { ApiRequestError } from "../api/client";
import { eliminarPaciente, listarPacientes } from "../api/pacientes";
import { PacienteForm } from "../components/paciente/PacienteForm";
import { EmptyState } from "../components/ui/EmptyState";
import { Icon } from "../components/ui/Icon";
import { Modal } from "../components/ui/Modal";
import { TableSkeleton } from "../components/ui/Skeleton";
import { useDialog } from "../components/ui/Dialog";
import { useToast } from "../components/ui/Toast";
import { useDebounce } from "../hooks/useDebounce";
import { useFetch } from "../hooks/useFetch";
import { fecha } from "../lib/format";
import type { Paciente } from "../types/paciente";

const PAGE_SIZE = 10;

// null = form cerrado; { paciente: null } = alta; { paciente } = edición.
type FormState = { paciente: Paciente | null } | null;

const initials = (nombre?: string, apellido?: string) =>
  `${nombre?.[0] ?? ""}${apellido?.[0] ?? ""}`.toUpperCase() || "·";

export function Pacientes() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const dq = useDebounce(q);
  const [form, setForm] = useState<FormState>(null);
  // Nota abierta en modal: en la celda no entra (una nota larga estiraba la fila y encima
  // quedaba cortada), así que el listado sólo dice si hay y el texto completo se abre acá.
  const [nota, setNota] = useState<Paciente | null>(null);
  const toast = useToast();
  const { confirmar } = useDialog();

  const fetcher = useCallback(
    (s: AbortSignal) => listarPacientes(dq, page, PAGE_SIZE, s),
    [dq, page],
  );
  const { data, loading, error, refetch } = useFetch(fetcher, [dq, page]);

  function onSearchChange(value: string) {
    setQ(value);
    setPage(0); // volver a la primera página al cambiar el filtro
  }

  function onSaved() {
    setForm(null);
    toast.success("Paciente guardado.");
    refetch();
  }

  async function onDelete(p: Paciente) {
    const ok = await confirmar({
      titulo: `¿Eliminar a ${p.nombre} ${p.apellido}?`,
      mensaje:
        "Se va a quitar de tu lista de pacientes. Sus bonos ya emitidos no se tocan. " +
        "Si tiene bonos pendientes, el sistema no te va a dejar.",
      confirmar: "Eliminar",
      peligro: true,
    });
    if (!ok) return;
    try {
      await eliminarPaciente(p.id);
      toast.success("Paciente eliminado.");
      refetch();
    } catch (err) {
      toast.error(
        err instanceof ApiRequestError ? err.message : "No se pudo eliminar el paciente.",
      );
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Pacientes</h1>
          <p className="muted">
            {data
              ? `${data.totalElements} paciente${data.totalElements === 1 ? "" : "s"}`
              : "Tu cartera de pacientes"}
          </p>
        </div>
        <button className="btn btn--primary" onClick={() => setForm({ paciente: null })}>
          <Icon name="plus" />
          Nuevo paciente
        </button>
      </div>

      <input
        className="picker__input search"
        placeholder="Buscar por nombre, apellido o email…"
        value={q}
        onChange={(e) => onSearchChange(e.target.value)}
      />

      {loading && <TableSkeleton rows={6} cols={5} />}
      {error && <div className="alert alert--error">{error}</div>}

      {data && data.content.length === 0 && (
        <EmptyState
          icon="users"
          title={q ? `Sin resultados para “${q}”` : "Todavía no hay pacientes"}
          hint={q ? "Probá con otro término." : "Cargá tu primer paciente para empezar a emitir bonos."}
          action={
            !q && (
              <button className="btn btn--primary" onClick={() => setForm({ paciente: null })}>
                <Icon name="plus" />
                Nuevo paciente
              </button>
            )
          }
        />
      )}

      {data && data.content.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Email</th>
                <th>WhatsApp</th>
                <th>Notas</th>
                <th>Alta</th>
                <th className="table__actions">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((p) => (
                <tr key={p.id}>
                  <td>
                    <span style={{ display: "flex", alignItems: "center", gap: "0.7rem" }}>
                      <span className="avatar">{initials(p.nombre, p.apellido)}</span>
                      <span style={{ fontWeight: 700 }}>
                        {p.nombre} {p.apellido}
                      </span>
                    </span>
                  </td>
                  <td>{p.email}</td>
                  <td className="muted">{p.whatsapp || "—"}</td>
                  <td className="cell-nota">
                    {p.notas ? (
                      <button className="btn btn--sm btn--ghost" onClick={() => setNota(p)}>
                        Ver nota
                      </button>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td className="muted">{fecha(p.createdAt)}</td>
                  <td className="table__actions">
                    <button
                      className="btn-icon"
                      title="Editar paciente"
                      aria-label={`Editar a ${p.nombre} ${p.apellido}`}
                      onClick={() => setForm({ paciente: p })}
                    >
                      <Icon name="pencil" size={16} />
                    </button>
                    <button
                      className="btn-icon btn-icon--danger"
                      title="Eliminar paciente"
                      aria-label={`Eliminar a ${p.nombre} ${p.apellido}`}
                      onClick={() => onDelete(p)}
                    >
                      <Icon name="trash" size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="pager">
            <button
              className="btn btn--sm btn--ghost"
              disabled={data.first}
              onClick={() => setPage((n) => Math.max(0, n - 1))}
            >
              ← Anterior
            </button>
            <span className="muted">
              Página {data.page + 1} de {data.totalPages} · {data.totalElements} pacientes
            </span>
            <button
              className="btn btn--sm btn--ghost"
              disabled={data.last}
              onClick={() => setPage((n) => n + 1)}
            >
              Siguiente →
            </button>
          </div>
        </>
      )}

      {nota && (
        <Modal
          title={`Nota de ${nota.nombre} ${nota.apellido}`}
          onClose={() => setNota(null)}
        >
          <p className="nota-texto">{nota.notas}</p>
        </Modal>
      )}

      {form && (
        <Modal
          title={form.paciente ? "Editar paciente" : "Nuevo paciente"}
          onClose={() => setForm(null)}
        >
          <PacienteForm
            paciente={form.paciente}
            onSaved={onSaved}
            onCancel={() => setForm(null)}
          />
        </Modal>
      )}
    </section>
  );
}
