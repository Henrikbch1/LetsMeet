package org.encoway.model;

import java.time.LocalDateTime;

/**
 * A directed "like" as read from a MongoDB user document, still referencing the raw
 * (unresolved) email addresses of both parties.
 */
public record MongoLike(String likerEmail, String likedEmail, String status, LocalDateTime likedAt) {
}
