package com.townbasket.shared;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Shared paginated API envelope: {@code {content, page, size, totalElements}}.
 *
 * <p>Lives in the OPEN {@code shared} module so every module's controllers can
 * return a consistent pagination shape to the generated TS client.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements) {

    /** Build an envelope from a Spring Data {@link Page} of already-mapped DTOs. */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    /** Build an envelope from a Spring Data {@link Page} of source rows, mapping each element. */
    public static <S, T> PagedResponse<T> of(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
