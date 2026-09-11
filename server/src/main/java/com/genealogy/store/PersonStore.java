package com.genealogy.store;

import com.genealogy.domain.person.Person;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class PersonStore {
    private final Map<UUID, Person> persons = new ConcurrentHashMap<>();

    public void addPerson(Person person) {
        persons.put(person.getId(), person);
    }

    public List<Person> listByFamily(UUID familyId) {
        return persons.values().stream()
                .filter(p -> p.getFamilyId().equals(familyId))
                .collect(Collectors.toList());
    }

    public boolean existsInFamily(UUID personId, UUID familyId) {
        Person person = persons.get(personId);
        return person != null && person.getFamilyId().equals(familyId);
    }

    public void clear() {
        persons.clear();
    }
}
