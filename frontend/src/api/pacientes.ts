import type { PageResponse } from "../types/common";
import type {
  Paciente,
  PacienteCreateRequest,
  PacienteUpdateRequest,
} from "../types/paciente";
import { api } from "./client";

/** Búsqueda para el picker de Emitir Receta (q sobre nombre/apellido/email, unaccent). */
export const buscarPacientes = (q: string, signal?: AbortSignal) =>
  api.get<PageResponse<Paciente>>("/pacientes", { q, size: 8 }, signal);

/** Listado paginado para la pantalla de Pacientes. */
export const listarPacientes = (
  q: string,
  page: number,
  size: number,
  signal?: AbortSignal,
) => api.get<PageResponse<Paciente>>("/pacientes", { q, page, size }, signal);

export const crearPaciente = (body: PacienteCreateRequest) =>
  api.post<Paciente>("/pacientes", body);

export const actualizarPaciente = (id: string, body: PacienteUpdateRequest) =>
  api.put<Paciente>(`/pacientes/${id}`, body);

export const eliminarPaciente = (id: string) => api.del<void>(`/pacientes/${id}`);
