package com.nutriapp.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginación para responses. Oculta el modelo interno {@link Page} de Spring Data
 * para no leakear su estructura al frontend.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
