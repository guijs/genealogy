package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphProjectionResponse {
    private final String familyId;
    private final String rootPersonId;
    private final int depth;
    private final boolean truncated;
    private final String truncateReason;
    private final List<PersonDTO> persons;
    private final List<MarriageDTO> marriages;
    private final List<RelationshipDTO> relationships;
    private final List<DerivedSiblingDTO> siblings;

    public GraphProjectionResponse(String familyId, String rootPersonId, int depth,
                                   boolean truncated, String truncateReason,
                                   List<PersonDTO> persons, List<MarriageDTO> marriages,
                                   List<RelationshipDTO> relationships) {
        this(familyId, rootPersonId, depth, truncated, truncateReason, persons, marriages, relationships, null);
    }

    public GraphProjectionResponse(String familyId, String rootPersonId, int depth,
                                   boolean truncated, String truncateReason,
                                   List<PersonDTO> persons, List<MarriageDTO> marriages,
                                   List<RelationshipDTO> relationships,
                                   List<DerivedSiblingDTO> siblings) {
        this.familyId = familyId;
        this.rootPersonId = rootPersonId;
        this.depth = depth;
        this.truncated = truncated;
        this.truncateReason = truncateReason;
        this.persons = persons;
        this.marriages = marriages;
        this.relationships = relationships;
        this.siblings = siblings;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getRootPersonId() {
        return rootPersonId;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getTruncateReason() {
        return truncateReason;
    }

    public List<PersonDTO> getPersons() {
        return persons;
    }

    public List<MarriageDTO> getMarriages() {
        return marriages;
    }

    public List<RelationshipDTO> getRelationships() {
        return relationships;
    }

    public List<DerivedSiblingDTO> getSiblings() {
        return siblings;
    }
}
