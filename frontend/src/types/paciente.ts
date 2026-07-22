// Espejo de PacienteResponse (GET /pacientes[/{id}]). Shape verificado contra el backend seeded.
// OJO: el GET (list y detalle) NO devuelve fechaNacimiento ni notas, aunque el POST sí los acepta/echoea.
// → el form de edición no puede precargarlos (ver DIARIO 2026-07-18, pendiente Santi).
export interface Paciente {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  whatsapp: string;
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

// PUT /pacientes/{id} — solo los 4 campos que el GET devuelve (round-trip seguro, sin pisar fecha/notas).
export interface PacienteUpdateRequest {
  nombre: string;
  apellido: string;
  email: string;
  whatsapp: string;
}
