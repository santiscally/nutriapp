// Toasts para feedback transitorio (éxito/error). Provider en la raíz + hook useToast().
// Uso: const toast = useToast(); toast.success("Paciente guardado"); toast.error(msg);
//
// Duraciones: el aviso aparece mientras la atención está en otra cosa (se cierra un modal, se
// refresca una tabla), así que 3,8s se perdían. Ahora el de éxito dura 6s, el de error **no se
// va solo** —es accionable, quien lo lee decide cuándo— y todos tienen una ✕ visible: antes el
// click cerraba, pero nada lo anunciaba. Pasar el mouse por encima congela la cuenta.

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Icon } from "./Icon";

type ToastKind = "success" | "error";
interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
}

const ToastContext = createContext<ToastApi | undefined>(undefined);

/** Sólo el de éxito se va solo; el de error espera a que lo cierren. */
const DURACION_MS: Record<ToastKind, number | null> = {
  success: 6000,
  error: null,
};

let seq = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  // Los timers viven en un ref y no en estado: cancelarlos y rearmarlos al pasar el mouse no
  // tiene que redibujar nada.
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const remove = useCallback((id: number) => {
    const t = timers.current.get(id);
    if (t) {
      clearTimeout(t);
      timers.current.delete(id);
    }
    setToasts((prev) => prev.filter((x) => x.id !== id));
  }, []);

  const armar = useCallback(
    (id: number, kind: ToastKind) => {
      const ms = DURACION_MS[kind];
      if (ms == null) return;
      timers.current.set(id, setTimeout(() => remove(id), ms));
    },
    [remove],
  );

  const pausar = useCallback((id: number) => {
    const t = timers.current.get(id);
    if (t) {
      clearTimeout(t);
      timers.current.delete(id);
    }
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string) => {
      const id = ++seq;
      setToasts((prev) => [...prev, { id, kind, message }]);
      armar(id, kind);
    },
    [armar],
  );

  // Desmontar el provider con timers vivos dejaría setState sobre un árbol que ya no existe.
  useEffect(() => {
    const pendientes = timers.current;
    return () => {
      pendientes.forEach((t) => clearTimeout(t));
      pendientes.clear();
    };
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      success: (m) => push("success", m),
      error: (m) => push("error", m),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`toast toast--${t.kind}`}
            onClick={() => remove(t.id)}
            onMouseEnter={() => pausar(t.id)}
            onMouseLeave={() => armar(t.id, t.kind)}
          >
            <Icon name={t.kind === "success" ? "check-circle" : "x-circle"} size={18} />
            <span className="toast__texto">{t.message}</span>
            <button
              type="button"
              className="toast__cerrar"
              aria-label="Cerrar el aviso"
              onClick={(e) => {
                e.stopPropagation();
                remove(t.id);
              }}
            >
              <Icon name="close" size={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast debe usarse dentro de <ToastProvider>");
  return ctx;
}
