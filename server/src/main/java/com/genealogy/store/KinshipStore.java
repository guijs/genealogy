package com.genealogy.store;

import com.genealogy.domain.kinship.KinshipGraph;
import com.genealogy.domain.kinship.Relation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KinshipStore {
    private final Map<UUID, KinshipGraph> graphs = new ConcurrentHashMap<>();

    public void addRelation(UUID familyId, Relation relation) {
        KinshipGraph graph = graphs.computeIfAbsent(familyId, k -> new KinshipGraph());
        graph.addRelation(relation);
    }

    public KinshipGraph getGraph(UUID familyId) {
        return graphs.computeIfAbsent(familyId, k -> new KinshipGraph());
    }

    public void clear() {
        graphs.clear();
    }
}
