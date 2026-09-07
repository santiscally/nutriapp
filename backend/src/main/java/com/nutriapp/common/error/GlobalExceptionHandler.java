package com.nutriapp.common.error;

import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.keycloak.KeycloakAdminException;
import com.nutriapp.modules.webhook.exception.WebhookSignatureException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req);
    }

    /** Archivo subido que no se puede procesar (formato, columnas faltantes). El mensaje es accionable. */
    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<ApiError> handleUnprocessable(UnprocessableException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE", ex.getMessage(), req);
    }

    /** Archivo más grande que el límite configurado (multipart). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "El archivo es demasiado grande", req);
    }

    /**
     * Adapter de integración en modo stub (o caído). 503 con mensaje claro: el frontend
     * lo muestra tal cual ("integración tiendanube no conectada").
     */
    @ExceptionHandler(IntegrationUnavailableException.class)
    public ResponseEntity<ApiError> handleIntegrationUnavailable(
            IntegrationUnavailableException ex, HttpServletRequest req) {
        log.info("Integración {} no disponible en {}", ex.getProveedor(), req.getRequestURI());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", ex.mensajeUsuario(), req);
    }

    /** Keycloak (nuestro server de identidad) inaccesible o con error: 503 con mensaje claro. */
    @ExceptionHandler(KeycloakAdminException.class)
    public ResponseEntity<ApiError> handleKeycloakAdmin(KeycloakAdminException ex, HttpServletRequest req) {
        log.error("Error contra Keycloak Admin en {}", req.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVER_UNAVAILABLE",
                "El servidor de identidad no está disponible, intentá más tarde", req);
    }

    /**
     * Firma HMAC de webhook inválida/ausente. 401: la firma ES la autenticación del webhook
     * (no hay JWT). No revela detalle de por qué falló (no filtrar pistas de verificación).
     */
    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<ApiError> handleWebhookSignature(WebhookSignatureException ex, HttpServletRequest req) {
        log.warn("Webhook con firma inválida en {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Firma de webhook inválida", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation", ex);
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY", "Violación de integridad de datos", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(),
                        fe.getDefaultMessage() == null ? "inválido" : fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.withErrors(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Error de validación",
                req.getRequestURI(),
                errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "Parámetro inválido: " + ex.getName(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Sin permisos", req);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleUnauthenticated(AuthenticationCredentialsNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Token inválido o ausente", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Falta el parámetro requerido: " + ex.getParameterName(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Método " + ex.getMethod() + " no soportado en esta ruta", req);
    }

    /**
     * Content-Type equivocado (típico: mandar JSON a un endpoint multipart, como {@code /registro}).
     * Sin este handler caía en el catch-all y salía 500 "Error interno": un error del cliente
     * reportado como falla del servidor, encima en un endpoint público.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        String soportados = ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining(", "));
        String detalle = soportados.isBlank() ? "" : " Se esperaba: " + soportados + ".";
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type no soportado en esta ruta." + detalle, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Cuerpo de la petición inválido (JSON malformado o valor de enum inexistente)", req);
    }

    /**
     * Proxy lazy contra una fila soft-deleted. Sin esto explota como 500 pelado
     * (lección imedba: guards de integridad + este handler).
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest req) {
        log.warn("Referencia a entidad eliminada en {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "DELETED_REFERENCE",
                "El registro referencia datos que fueron eliminados", req);
    }

    /**
     * Ruta inexistente. Sin este handler la atrapa el catch-all de abajo y sale un 500 "Error
     * interno" — el front muestra "algo se rompió" cuando en realidad pidió una URL que no existe,
     * y en los logs queda un stacktrace de error por cada 404. Aparece cada vez que se retira un
     * endpoint y algún cliente viejo lo sigue llamando.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleRutaInexistente(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no existe", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Error no manejado en {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Error interno", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String msg, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, msg, req.getRequestURI()));
    }
}
