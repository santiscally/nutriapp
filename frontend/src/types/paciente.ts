// Espejo de PacienteResponse (GET /pacientes[/{id}]). Shape verificado contra el backend.
export interface Paciente {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  whatsapp: string;
  /** ISO date (YYYY-MM-DD). Ausente si no se cargó. */
  fechaNacimiento?: string | null;
  /** Notas de la nutricionista sobre la paciente. Editables desde el form. */
  notas?: string | null;
  createdAt: string;
}

// POST /pacientes — fechaNacimiento y notas son opcionales (se capturan al alta).
export interface PacienteCreateRequest {
  nombre: string;
  apellido: string;
  email: string;
  whatsapp: string;
  fechaNacimiento?: string; // ISO date (YYYY-MM-DD)
  notas?: string;
}

// PUT /pacientes/{id} — mismos campos que el alta: el GET los devuelve, así que el form puede
// precargarlos y no hay riesgo de pisar fecha/notas al guardar.
export interface PacienteUpdateRequest {
  nombre: string;
  apellido: string;
  email: string;
  whatsapp: string;
  fechaNacimiento?: string;
  notas?: string;
}
