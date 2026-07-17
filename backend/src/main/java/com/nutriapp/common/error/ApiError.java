package com.nutriapp.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Formato uniforme de error. Consumido por el frontend (api/client.ts surfacea `message`).
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> errors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError withErrors(int status, String error, String message, String path, List<FieldError> errors) {
        return new ApiError(Instant.now(), status, error, message, path, errors);
    }
}
