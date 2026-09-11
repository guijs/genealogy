package com.genealogy.store;

import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.mapper.PersonMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PersonStore {
    private final PersonMapper personMapper;

    public PersonStore(PersonMapper personMapper) {
        this.personMapper = personMapper;
    }

    public void addPerson(Person person) {
        String displayName = buildDisplayName(person.getFirstName(), person.getLastName());
        personMapper.insert(
                person.getId(),
                person.getFamilyId(),
                person.getFirstName(),
                person.getLastName(),
                displayName,
                null,
                null,
                null,
                false
        );
    }

    public Optional<Person> getPerson(UUID id) {
        return Optional.ofNullable(personMapper.findById(id));
    }

    public List<Person> listByFamily(UUID familyId) {
        return personMapper.findByFamilyId(familyId);
    }

    public boolean existsInFamily(UUID personId, UUID familyId) {
        return personMapper.existsByIdAndFamilyId(personId, familyId);
    }

    public void replace(Person person) {
        Optional<ProjectionPerson> existingProjection = Optional.ofNullable(personMapper.findProjectionById(person.getId()));
        String displayName = buildDisplayName(person.getFirstName(), person.getLastName());
        
        if (existingProjection.isPresent()) {
            ProjectionPerson proj = existingProjection.get();
            personMapper.update(
                    person.getId(),
                    person.getFirstName(),
                    person.getLastName(),
                    displayName,
                    proj.getGender() != null ? proj.getGender().getValue() : null,
                    proj.getBirthYear(),
                    proj.getDeathYear(),
                    proj.isHidden()
            );
        } else {
            personMapper.update(
                    person.getId(),
                    person.getFirstName(),
                    person.getLastName(),
                    displayName,
                    null,
                    null,
                    null,
                    false
            );
        }
    }

    public long countByFamily(UUID familyId) {
        return personMapper.countByFamilyId(familyId);
    }

    private String buildDisplayName(String firstName, String lastName) {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) {
            sb.append(firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(lastName);
        }
        return sb.toString().trim();
    }

    public void clear() {
        personMapper.deleteAll();
    }
}
