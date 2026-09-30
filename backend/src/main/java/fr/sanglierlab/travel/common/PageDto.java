package fr.sanglierlab.travel.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Enveloppe de pagination exposée par l'API.
 * Évite de sérialiser directement un {@link Page} Spring, dont le format
 * est verbeux et instable d'une version à l'autre.
 */
public record PageDto<T>(
        List<T> content,
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean first,
        boolean last
) {

    public static <E, T> PageDto<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageDto<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.isFirst(),
                page.isLast());
    }
}
