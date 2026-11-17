package com.trustlayer.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope. It avoids coupling public REST responses to Spring Data's
 * internal serialization format.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
