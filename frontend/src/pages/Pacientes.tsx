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
import { useToast } from "../components/ui/Toast";
import { useDebounce } from "../hooks/useDebounce";
import { useFetch } from "../hooks/useFetch";
import { fecha } from "../lib/format";
import type { Paciente } from "../types/paciente";

const PAGE_SIZE = 10;

// null = form cerrado; { paciente: null } = alta; { paciente } = edición.
type FormState = { paciente: Paciente | null } | null;

export function Pacientes() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const dq = useDebounce(q);
  const [form, setForm] = useState<FormState>(null);
  const toast = useToast();

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
    if (!window.confirm(`¿Eliminar a ${p.nombre} ${p.apellido}?`)) return;
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
        <h1 className="page-title">Pacientes</h1>
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
          hint={q ? "Probá con otro término." : "Cargá tu primer paciente para empezar a emitir recetas."}
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
                <th>Alta</th>
                <th aria-label="acciones" />
              </tr>
            </thead>
            <tbody>
              {data.content.map((p) => (
                <tr key={p.id}>
                  <td>
                    {p.nombre} {p.apellido}
                  </td>
                  <td>{p.email}</td>
                  <td className="mono">{p.whatsapp}</td>
                  <td className="muted">{fecha(p.createdAt)}</td>
                  <td className="table__actions">
                    <button
                      className="btn btn--sm btn--ghost"
                      onClick={() => setForm({ paciente: p })}
                    >
                      Editar
                    </button>
                    <button className="btn btn--sm btn--ghost" onClick={() => onDelete(p)}>
                      Eliminar
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
