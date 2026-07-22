package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic wrapper for paginated list responses, used across all controllers
 * that expose paged endpoints (e.g. item listings, movement history).
 *
 * @param <T> the type of element contained in the page
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    @Schema(description = "The elements on the current page")
    private List<T> content;

    @Schema(description = "Current page number, zero-based", example = "0")
    private int page;

    @Schema(description = "Number of elements requested per page", example = "20")
    private int size;

    @Schema(description = "Total number of elements across all pages", example = "134")
    private long totalElements;

    @Schema(description = "Total number of pages available", example = "7")
    private int totalPages;

    /**
     * Wraps a Spring Data {@link Page} directly, keeping the same element type.
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    /**
     * Wraps a Spring Data {@link Page}, mapping each element (e.g. entity to DTO)
     * via the supplied mapper while preserving pagination metadata.
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
