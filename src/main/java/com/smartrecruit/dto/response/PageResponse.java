package com.smartrecruit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(name = "PageResponse", description = "Page de résultats")
public record PageResponse<T>(

        @Schema(description = "Contenu de la page")
        List<T> content,

        @Schema(example = "0")
        int page,

        @Schema(example = "10")
        int size,

        @Schema(example = "42")
        long totalElements,

        @Schema(example = "5")
        int totalPages,

        @Schema(example = "false")
        boolean last
) {
    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
