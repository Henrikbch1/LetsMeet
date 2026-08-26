package org.encoway.source.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.encoway.model.MongoData;
import org.encoway.model.MongoLike;
import org.encoway.model.MongoMessage;
import org.encoway.model.MongoProfile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the MongoDB {@code LetsMeet.users} collection: supplemental profile data plus the
 * directed likes and messages nested in each user document. Friends are always empty per the
 * data contract and are intentionally not read.
 */
public class MongoDataReader {

    private static final String DEFAULT_CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DEFAULT_DATABASE_NAME = "LetsMeet";
    private static final String DEFAULT_COLLECTION_NAME = "users";

    // "u" (year) is used instead of "y" (year-of-era): with ResolverStyle.STRICT, "y" requires
    // an era field to resolve, which these timestamps do not have.
    private static final DateTimeFormatter ISO_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter GERMAN_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final String connectionString;
    private final String databaseName;
    private final String collectionName;

    public MongoDataReader() {
        this(DEFAULT_CONNECTION_STRING, DEFAULT_DATABASE_NAME, DEFAULT_COLLECTION_NAME);
    }

    public MongoDataReader(String connectionString, String databaseName, String collectionName) {
        this.connectionString = connectionString;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    public MongoData readMongoData() {
        try (MongoClient client = MongoClients.create(connectionString)) {
            MongoDatabase database = client.getDatabase(databaseName);
            MongoCollection<Document> users = database.getCollection(collectionName);
            return mapUsers(users);
        }
    }

    private MongoData mapUsers(MongoCollection<Document> users) {
        List<MongoProfile> profiles = new ArrayList<>();
        List<MongoLike> likes = new ArrayList<>();
        List<MongoMessage> messages = new ArrayList<>();
        Set<String> seenLowerEmails = new HashSet<>();

        for (Document user : users.find()) {
            String email = requireText(user, "_id");
            String lowerEmail = email.toLowerCase(Locale.ROOT);
            if (!seenLowerEmails.add(lowerEmail)) {
                throw new IllegalStateException(
                        "Duplicate case-insensitive Mongo user identity detected for a user email.");
            }

            Name name = splitName(requireText(user, "name"));
            profiles.add(new MongoProfile(email, name.firstName(), name.lastName(), user.getString("phone")));

            for (Document like : user.getList("likes", Document.class, List.of())) {
                likes.add(new MongoLike(
                        email,
                        requireText(like, "liked_email"),
                        requireText(like, "status"),
                        parseTimestamp(requireText(like, "timestamp"))
                ));
            }

            for (Document message : user.getList("messages", Document.class, List.of())) {
                Integer conversationId = message.getInteger("conversation_id");
                if (conversationId == null) {
                    throw new IllegalStateException(
                            "Missing or non-numeric conversation_id in a Mongo message entry.");
                }
                messages.add(new MongoMessage(
                        email,
                        requireText(message, "receiver_email"),
                        conversationId,
                        requireText(message, "message"),
                        parseTimestamp(requireText(message, "timestamp"))
                ));
            }
        }

        return new MongoData(profiles, likes, messages);
    }

    private String requireText(Document document, String field) {
        String value = document.getString(field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required Mongo field '" + field + "'.");
        }
        return value;
    }

    private LocalDateTime parseTimestamp(String rawTimestamp) {
        try {
            return LocalDateTime.parse(rawTimestamp, ISO_TIMESTAMP_FORMAT);
        } catch (DateTimeParseException isoException) {
            try {
                return LocalDateTime.parse(rawTimestamp, GERMAN_TIMESTAMP_FORMAT);
            } catch (DateTimeParseException germanException) {
                IllegalArgumentException unknownFormat = new IllegalArgumentException(
                        "Unknown Mongo timestamp format: " + rawTimestamp);
                unknownFormat.addSuppressed(isoException);
                unknownFormat.addSuppressed(germanException);
                throw unknownFormat;
            }
        }
    }

    private Name splitName(String fullName) {
        int separatorIndex = fullName.indexOf(", ");
        if (separatorIndex < 0) {
            throw new IllegalStateException(
                    "Malformed Mongo user name, expected the 'Nachname, Vorname' format.");
        }
        return new Name(fullName.substring(separatorIndex + 2), fullName.substring(0, separatorIndex));
    }

    private record Name(String firstName, String lastName) {
    }
}
