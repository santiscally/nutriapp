// Diálogos de confirmación y de captura de texto, con la estética de la app.
//
// Reemplazan a window.confirm / window.prompt, que además de verse como una alerta del navegador
// (tipografía del sistema, botones "Aceptar/Cancelar" en inglés según el idioma del browser) son
// bloqueantes y no dejan explicar nada: en un borrado irreversible hace falta decir qué se pierde y
// qué alternativa hay, y eso no entra en una línea de texto plano.
//
// La API es promesa-based para que el call site siga leyéndose como el `if (!confirm(...)) return;`
// de antes:
//
//   if (!(await confirmar({ titulo: "…", mensaje: "…" }))) return;
//   const motivo = await pedirTexto({ titulo: "…" });   // null = canceló

import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";
import { Modal } from "./Modal";

interface ConfirmarOpts {
  titulo: string;
  mensaje?: ReactNode;
  /** Texto del botón que confirma. Debe nombrar la acción ("Borrar"), no decir "Aceptar". */
  confirmar?: string;
  cancelar?: string;
  /** Acción destructiva: pinta el botón en rojo. */
  peligro?: boolean;
}

interface TextoOpts {
  titulo: string;
  mensaje?: ReactNode;
  etiqueta: string;
  placeholder?: string;
  /** `password` oculta lo tipeado: se usa para la contraseña que setea el admin. */
  tipo?: "text" | "password";
  confirmar?: string;
  /** Si devuelve un string, se muestra como error y no se cierra. */
  validar?: (valor: string) => string | null;
  /** Permite confirmar con el campo vacío (motivo de rechazo opcional). */
  opcional?: boolean;
  multilinea?: boolean;
}

interface DialogApi {
  confirmar: (opts: ConfirmarOpts) => Promise<boolean>;
  pedirTexto: (opts: TextoOpts) => Promise<string | null>;
}

const DialogContext = createContext<DialogApi | null>(null);

type Pendiente =
  | { tipo: "confirmar"; opts: ConfirmarOpts }
  | { tipo: "texto"; opts: TextoOpts };

export function DialogProvider({ children }: { children: ReactNode }) {
  const [pendiente, setPendiente] = useState<Pendiente | null>(null);
  const [valor, setValor] = useState("");
  const [error, setError] = useState<string | null>(null);
  // El resolve de la promesa en curso: lo guarda un ref para que cerrar el diálogo desde cualquier
  // camino (botón, Escape, click afuera) resuelva siempre, y nunca deje al llamador colgado.
  const resolver = useRef<((v: unknown) => void) | null>(null);

  const cerrar = useCallback((resultado: boolean | string | null) => {
    resolver.current?.(resultado);
    resolver.current = null;
    setPendiente(null);
    setValor("");
    setError(null);
  }, []);

  const confirmar = useCallback((opts: ConfirmarOpts) => {
    setPendiente({ tipo: "confirmar", opts });
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve as (v: unknown) => void;
    });
  }, []);

  const pedirTexto = useCallback((opts: TextoOpts) => {
    setPendiente({ tipo: "texto", opts });
    setValor("");
    return new Promise<string | null>((resolve) => {
      resolver.current = resolve as (v: unknown) => void;
    });
  }, []);

  function aceptarTexto() {
    if (pendiente?.tipo !== "texto") return;
    const { opts } = pendiente;
    const v = valor.trim();
    if (!opts.opcional && v === "") {
      setError("Completá este campo.");
      return;
    }
    const err = opts.validar?.(v);
    if (err) {
      setError(err);
      return;
    }
    cerrar(v);
  }

  return (
    <DialogContext.Provider value={{ confirmar, pedirTexto }}>
      {children}

      {pendiente?.tipo === "confirmar" && (
        <Modal title={pendiente.opts.titulo} onClose={() => cerrar(false)}>
          <div className="dialog">
            {pendiente.opts.mensaje && <div className="dialog__mensaje">{pendiente.opts.mensaje}</div>}
            <div className="dialog__acciones">
              <button className="btn btn--ghost" onClick={() => cerrar(false)}>
                {pendiente.opts.cancelar ?? "Cancelar"}
              </button>
              <button
                className={"btn " + (pendiente.opts.peligro ? "btn--danger-solid" : "btn--primary")}
                onClick={() => cerrar(true)}
                autoFocus
              >
                {pendiente.opts.confirmar ?? "Confirmar"}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {pendiente?.tipo === "texto" && (
        <Modal title={pendiente.opts.titulo} onClose={() => cerrar(null)}>
          <form
            className="dialog"
            onSubmit={(e) => {
              e.preventDefault();
              aceptarTexto();
            }}
          >
            {pendiente.opts.mensaje && <div className="dialog__mensaje">{pendiente.opts.mensaje}</div>}
            <label className="field">
              <span>{pendiente.opts.etiqueta}</span>
              {pendiente.opts.multilinea ? (
                <textarea
                  rows={3}
                  autoFocus
                  placeholder={pendiente.opts.placeholder}
                  value={valor}
                  onChange={(e) => {
                    setValor(e.target.value);
                    setError(null);
                  }}
                />
              ) : (
                <input
                  type={pendiente.opts.tipo ?? "text"}
                  autoFocus
                  autoComplete={pendiente.opts.tipo === "password" ? "new-password" : undefined}
                  placeholder={pendiente.opts.placeholder}
                  value={valor}
                  onChange={(e) => {
                    setValor(e.target.value);
                    setError(null);
                  }}
                />
              )}
            </label>
            {error && <div className="alert alert--error">{error}</div>}
            <div className="dialog__acciones">
              <button type="button" className="btn btn--ghost" onClick={() => cerrar(null)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn--primary">
                {pendiente.opts.confirmar ?? "Guardar"}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </DialogContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useDialog(): DialogApi {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error("useDialog fuera de DialogProvider");
  return ctx;
}
