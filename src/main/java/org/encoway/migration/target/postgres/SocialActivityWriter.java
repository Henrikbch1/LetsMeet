package org.encoway.migration.target.postgres;

import org.encoway.migration.model.PersonLike;
import org.encoway.migration.model.PersonMessage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes {@link PersonLike} and {@link PersonMessage} rows, the directed social interactions
 * between people.
 */
class SocialActivityWriter {

    protected void insertPersonLikes(Connection connection, List<PersonLike> personLikes) throws SQLException {
        BatchInserter.executeBatch(connection, """
                INSERT INTO %s (like_id, liker_person_id, liked_person_id, status, liked_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(DatabaseObjectNames.TABLE_PERSON_LIKE), personLikes, (statement, personLike) -> {
                    statement.setInt(1, personLike.likeId());
                    statement.setInt(2, personLike.likerPersonId());
                    statement.setInt(3, personLike.likedPersonId());
                    statement.setString(4, personLike.status());
                    statement.setObject(5, personLike.likedAt());
                });
    }

    protected void insertPersonMessages(Connection connection, List<PersonMessage> personMessages) throws SQLException {
        BatchInserter.executeBatch(connection, """
                INSERT INTO %s (
                    message_id, sender_person_id, receiver_person_id, conversation_id, body, sent_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(DatabaseObjectNames.TABLE_PERSON_MESSAGE), personMessages, (statement, personMessage) -> {
                    statement.setInt(1, personMessage.messageId());
                    statement.setInt(2, personMessage.senderPersonId());
                    statement.setInt(3, personMessage.receiverPersonId());
                    statement.setInt(4, personMessage.conversationId());
                    statement.setString(5, personMessage.body());
                    statement.setObject(6, personMessage.sentAt());
                });
    }
}
