package org.encoway.migration.domain.model;

import java.time.LocalDateTime;

/**
 * A directed message relation resolved to internal person ids, ready for insertion.
 */
public record PersonMessage(
        Integer messageId,
        Integer senderPersonId,
        Integer receiverPersonId,
        Integer conversationId,
        String body,
        LocalDateTime sentAt
) {
}
