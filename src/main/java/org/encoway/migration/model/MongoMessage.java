package org.encoway.migration.model;

import java.time.LocalDateTime;

/**
 * A directed message as read from a MongoDB user document, still referencing the raw
 * (unresolved) email addresses of both parties.
 */
public record MongoMessage(
        String senderEmail,
        String receiverEmail,
        Integer conversationId,
        String body,
        LocalDateTime sentAt
) {
}
