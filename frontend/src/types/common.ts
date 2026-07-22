// Espejo del contrato back → front. Fuente de verdad: Swagger + instrucciones_claude/05-api-endpoints.md
// Sin codegen: se sincroniza a mano. Un cambio de DTO en el back llega por DIARIO.md ("Impacto para el otro").

/** Respuesta paginada unificada del backend (PageResponse<T>). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Cuerpo de error uniforme del GlobalExceptionHandler. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  errors?: { field?: string; message: string }[];
}
