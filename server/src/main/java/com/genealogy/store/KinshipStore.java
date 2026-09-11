package com.genealogy.store;

import com.genealogy.domain.kinship.KinshipGraph;
import com.genealogy.domain.kinship.Relation;
import com.genealogy.domain.kinship.RelationType;
import com.genealogy.domain.projection.ParentChildSubtype;
import com.genealogy.domain.projection.ParentRole;
import com.genealogy.domain.projection.ProjectionRelationship;
import com.genealogy.mapper.RelationshipMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KinshipStore {
    private final RelationshipMapper relationshipMapper;
    private final Map<UUID, KinshipGraph> graphCache = new ConcurrentHashMap<>();

    public KinshipStore(RelationshipMapper relationshipMapper) {
        this.relationshipMapper = relationshipMapper;
    }

    public void addRelation(UUID familyId, Relation relation) {
        KinshipGraph graph = getGraph(familyId);
        graph.addRelation(relation);
    }

    public KinshipGraph getGraph(UUID familyId) {
        return graphCache.computeIfAbsent(familyId, this::buildGraph);
    }

    private KinshipGraph buildGraph(UUID familyId) {
        KinshipGraph graph = new KinshipGraph();
        List<ProjectionRelationship> relationships = relationshipMapper.findByFamilyId(familyId);
        for (ProjectionRelationship rel : relationships) {
            if (rel.isDissolved()) {
                continue;
            }
            RelationType type = mapToRelationType(rel.getSubtype(), rel.getRole());
            if (type != null) {
                try {
                    graph.addRelation(new Relation(rel.getParentId(), rel.getChildId(), type));
                } catch (Exception e) {
                    // Skip invalid relations that may have been imported
                }
            }
        }
        return graph;
    }

    private RelationType mapToRelationType(ParentChildSubtype subtype, ParentRole role) {
        if (subtype == null || role == null) {
            return null;
        }
        return switch (subtype) {
            case BIOLOGICAL -> switch (role) {
                case FATHER -> RelationType.BIOLOGICAL_FATHER;
                case MOTHER -> RelationType.BIOLOGICAL_MOTHER;
                case PARENT -> RelationType.BIOLOGICAL_FATHER;
            };
            case ADOPTIVE -> switch (role) {
                case FATHER -> RelationType.ADOPTIVE_FATHER;
                case MOTHER -> RelationType.ADOPTIVE_MOTHER;
                case PARENT -> RelationType.ADOPTIVE_FATHER;
            };
        };
    }

    public void invalidateCache(UUID familyId) {
        graphCache.remove(familyId);
    }

    public void clear() {
        graphCache.clear();
    }
}
