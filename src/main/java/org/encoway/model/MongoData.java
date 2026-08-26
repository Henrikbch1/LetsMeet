package org.encoway.model;

import java.util.List;

/**
 * All data read from the MongoDB {@code LetsMeet.users} collection, before profiles are
 * merged with Excel data and likes/messages are resolved to internal person ids.
 */
public record MongoData(List<MongoProfile> profiles, List<MongoLike> likes, List<MongoMessage> messages) {

    public MongoData {
        profiles = List.copyOf(profiles);
        likes = List.copyOf(likes);
        messages = List.copyOf(messages);
    }
}
