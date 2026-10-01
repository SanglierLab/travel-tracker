package fr.sanglierlab.traveltracker.gallery;

import java.util.List;

/** Une page de résultats. {@code page} commence à 1. */
public record PageResponse<T>(List<T> content, int page, int pageSize, long totalElements, int totalPages) {
}
