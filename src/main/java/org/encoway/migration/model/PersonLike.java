package org.encoway.migration.model;

import java.time.LocalDateTime;

/**
 * A directed "like" relation resolved to internal person ids, ready for insertion.
 */
public record PersonLike(
        Integer likeId,
        Integer likerPersonId,
        Integer likedPersonId,
        String status,
        LocalDateTime likedAt
) {
}
