package org.encoway.migration.assembly;

import org.encoway.migration.model.MongoLike;
import org.encoway.migration.model.MongoMessage;
import org.encoway.migration.model.Person;
import org.encoway.migration.model.PersonLike;
import org.encoway.migration.model.PersonMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Indexes {@link Person} records by lower-cased email and resolves Mongo likes/messages to
 * internal person ids, assigning sequential ids starting at 1 in list order.
 */
final class RelationResolver {

    private final Map<String, Integer> personIdByLowerEmail;

    protected RelationResolver(List<Person> people) {
        this.personIdByLowerEmail = indexPeople(people);
    }

    private static Map<String, Integer> indexPeople(List<Person> people) {
        Map<String, Integer> personIdByLowerEmail = new HashMap<>();
        for (Person person : people) {
            String lowerEmail = person.email().toLowerCase(Locale.ROOT);
            if (personIdByLowerEmail.putIfAbsent(lowerEmail, person.personId()) != null) {
                throw new IllegalStateException(
                        "Duplicate case-insensitive person identity detected in Excel data for person id "
                                + person.personId() + ".");
            }
        }
        return personIdByLowerEmail;
    }

    protected List<PersonLike> resolveLikes(List<MongoLike> mongoLikes) {
        return resolveRelations(mongoLikes, 1, (like, likeId, resolver) -> new PersonLike(
                likeId,
                resolver.resolve(like.likerEmail()),
                resolver.resolve(like.likedEmail()),
                like.status(),
                like.likedAt()));
    }

    protected List<PersonMessage> resolveMessages(List<MongoMessage> mongoMessages) {
        return resolveRelations(mongoMessages, 1, (message, messageId, resolver) -> new PersonMessage(
                messageId,
                resolver.resolve(message.senderEmail()),
                resolver.resolve(message.receiverEmail()),
                message.conversationId(),
                message.body(),
                message.sentAt()));
    }

    private <T, R> List<R> resolveRelations(List<T> relations, int startId, RelationMapper<T, R> mapper) {
        List<R> resolvedRelations = new ArrayList<>(relations.size());
        int relationId = startId;
        PersonIdResolver resolver = this::resolvePersonId;
        for (T relation : relations) {
            resolvedRelations.add(mapper.map(relation, relationId++, resolver));
        }
        return resolvedRelations;
    }

    private int resolvePersonId(String email) {
        Integer personId = personIdByLowerEmail.get(email.toLowerCase(Locale.ROOT));
        if (personId == null) {
            throw new IllegalStateException(
                    "Unresolved relation reference: no matching person found for the referenced email.");
        }
        return personId;
    }

    @FunctionalInterface
    private interface PersonIdResolver {
        int resolve(String email);
    }

    @FunctionalInterface
    private interface RelationMapper<T, R> {
        R map(T relation, int relationId, PersonIdResolver personIdResolver);
    }
}
