// Configuración de parámetros de negocio (solo ADMIN): % de descuento (fijo global) y % de comisión.
// GET /configuracion + PUT /admin/configuracion. Ruta protegida: si no es admin, redirige.

import { useEffect, useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { ApiRequestError } from "../api/client";
import { getConfiguracion, updateConfiguracion } from "../api/configuracion";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../components/ui/Toast";

const validPct = (n: number) => !Number.isNaN(n) && n >= 0 && n <= 100;

export function Configuracion() {
  const { me } = useAuth();
  const toast = useToast();
  const [descuentoPct, setDescuentoPct] = useState("");
  const [comisionPct, setComisionPct] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const esAdmin = me?.roles.includes("ADMIN") ?? false;

  useEffect(() => {
    if (!esAdmin) return;
    const controller = new AbortController();
    getConfiguracion(controller.signal)
      .then((c) => {
        setDescuentoPct(String(c.descuentoPct));
        setComisionPct(String(c.comisionPct));
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) {
          setError(e instanceof Error ? e.message : "No se pudo cargar la configuración.");
        }
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [esAdmin]);

  if (!esAdmin) return <Navigate to="/dashboard" replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const d = Number(descuentoPct);
    const c = Number(comisionPct);
    if (!validPct(d) || !validPct(c)) {
      setError("Los porcentajes deben estar entre 0 y 100.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateConfiguracion({ descuentoPct: d, comisionPct: c });
      toast.success("Configuración guardada.");
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "No se pudo guardar la configuración.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1 className="page-title">Configuración</h1>
          <p className="muted">Parámetros de negocio de la plataforma.</p>
        </div>
      </div>

      {loading ? (
        <p className="muted">Cargando…</p>
      ) : (
        <form className="card" style={{ maxWidth: 540 }} onSubmit={onSubmit}>
          <div className="form-grid">
            <label className="field field--full">
              <span>Descuento de recetas (%)</span>
              <input
                type="number"
                min={0}
                max={100}
                step="0.01"
                value={descuentoPct}
                onChange={(e) => setDescuentoPct(e.target.value)}
              />
              <small className="muted">
                Fijo global: se aplica a toda receta emitida. El nutricionista no lo edita.
              </small>
            </label>
            <label className="field field--full">
              <span>Comisión del nutricionista (%)</span>
              <input
                type="number"
                min={0}
                max={100}
                step="0.01"
                value={comisionPct}
                onChange={(e) => setComisionPct(e.target.value)}
              />
              <small className="muted">
                Se aplica cuando una receta se convierte en compra pagada. Solo afecta conversiones
                futuras.
              </small>
            </label>
          </div>

          {error && <div className="alert alert--error">{error}</div>}

          <div className="form-actions">
            <button className="btn btn--primary" type="submit" disabled={saving}>
              {saving ? "Guardando…" : "Guardar cambios"}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
