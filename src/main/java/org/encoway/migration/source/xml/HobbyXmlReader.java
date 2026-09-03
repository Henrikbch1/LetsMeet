package org.encoway.migration.source.xml;

import org.encoway.migration.model.Hobby;
import org.encoway.migration.model.Person;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the root {@code Lets_Meet_Hobbies.xml} file: 100 users with 300 individual hobby
 * assignments in total. Each user is matched to an existing {@link Person} by case-insensitive
 * email; unresolved users are skipped. A person/hobby fact that already exists (from Excel or
 * from an earlier entry in this file) is emitted only once: the existing assignment wins.
 * Accepted facts are emitted with {@code source = "xml"} and {@code priority = null}.
 */
public class HobbyXmlReader {

    public static final String SOURCE_XML = "xml";

    private static final Path DEFAULT_PATH = Path.of("Lets_Meet_Hobbies.xml");
    private static final String USER_TAG = "user";
    private static final String EMAIL_TAG = "email";
    private static final String HOBBY_TAG = "hobby";

    public List<Hobby> readHobbies(List<Person> people, List<Hobby> existingHobbies) {
        return readHobbies(DEFAULT_PATH, people, existingHobbies);
    }

    public List<Hobby> readHobbies(Path xmlPath, List<Person> people, List<Hobby> existingHobbies) {
        Document document = parseSecurely(xmlPath);
        Map<String, Integer> personIdByLowerEmail = indexPeopleByLowerEmail(people);
        Set<PersonHobbyKey> seenFacts = indexExistingFacts(existingHobbies);
        int nextHobbyId = nextHobbyId(existingHobbies);

        List<Hobby> newHobbies = new ArrayList<>();
        NodeList userNodes = document.getElementsByTagName(USER_TAG);
        for (int i = 0; i < userNodes.getLength(); i++) {
            Element userElement = (Element) userNodes.item(i);
            Integer personId = resolvePersonId(userElement, personIdByLowerEmail);
            if (personId == null) {
                continue;
            }
            NodeList hobbyNodes = userElement.getElementsByTagName(HOBBY_TAG);
            for (int j = 0; j < hobbyNodes.getLength(); j++) {
                String description = hobbyNodes.item(j).getTextContent().strip();
                if (description.isEmpty()) {
                    continue;
                }
                PersonHobbyKey key = new PersonHobbyKey(personId, description);
                if (!seenFacts.add(key)) {
                    // Same person/hobby fact already recorded by Excel or an earlier XML entry;
                    // the existing assignment wins and this one is not duplicated.
                    continue;
                }
                newHobbies.add(new Hobby(nextHobbyId++, personId, description, null, SOURCE_XML));
            }
        }
        return newHobbies;
    }

    private Integer resolvePersonId(Element userElement, Map<String, Integer> personIdByLowerEmail) {
        NodeList emailNodes = userElement.getElementsByTagName(EMAIL_TAG);
        if (emailNodes.getLength() == 0) {
            return null;
        }
        String email = emailNodes.item(0).getTextContent().strip();
        return personIdByLowerEmail.get(email.toLowerCase(Locale.ROOT));
    }

    private Map<String, Integer> indexPeopleByLowerEmail(List<Person> people) {
        Map<String, Integer> personIdByLowerEmail = new HashMap<>();
        for (Person person : people) {
            personIdByLowerEmail.put(person.email().toLowerCase(Locale.ROOT), person.personId());
        }
        return personIdByLowerEmail;
    }

    private Set<PersonHobbyKey> indexExistingFacts(List<Hobby> existingHobbies) {
        Set<PersonHobbyKey> facts = new HashSet<>();
        for (Hobby hobby : existingHobbies) {
            facts.add(new PersonHobbyKey(hobby.userId(), hobby.description()));
        }
        return facts;
    }

    private int nextHobbyId(List<Hobby> existingHobbies) {
        int maxHobbyId = 0;
        for (Hobby hobby : existingHobbies) {
            maxHobbyId = Math.max(maxHobbyId, hobby.hobbyId());
        }
        return maxHobbyId + 1;
    }

    /**
     * Reads the file leniently as UTF-8 (malformed byte sequences become the U+FFFD replacement
     * character rather than aborting the read) and then parses the resulting text with a parser
     * hardened against external entities and DTDs.
     */
    private Document parseSecurely(Path xmlPath) {
        try {
            String xmlContent = new String(Files.readAllBytes(xmlPath), StandardCharsets.UTF_8);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read hobby XML file: " + xmlPath, exception);
        } catch (SAXException | ParserConfigurationException exception) {
            throw new IllegalStateException("Could not parse hobby XML file: " + xmlPath, exception);
        }
    }

    private record PersonHobbyKey(Integer personId, String description) {
    }
}
