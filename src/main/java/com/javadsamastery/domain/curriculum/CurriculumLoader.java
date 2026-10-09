package com.javadsamastery.domain.curriculum;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.topic.CurriculumGraph;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CurriculumLoader {

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static CurriculumGraph loadCurriculum(String resourcePath) {
        try (InputStream is = CurriculumLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Curriculum resource not found: " + resourcePath);
            }
            return loadCurriculum(is);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load curriculum from " + resourcePath, e);
        }
    }
    
    public static CurriculumGraph loadCurriculum(InputStream is) {
        try {
            CurriculumDefinition def = mapper.readValue(is, CurriculumDefinition.class);
            validateDefinition(def);
            
            CurriculumGraph graph = new CurriculumGraph();
            for (TopicDefinition td : def.topics()) {
                graph.addDefinition(td);
                if (td.prerequisites() != null) {
                    for (String prereq : td.prerequisites()) {
                        graph.addDependency(td.topicId(), prereq);
                    }
                } else {
                    graph.addDependency(td.topicId(), null); // Just registers the topic if no prereq logic
                }
            }
            
            validateAcyclic(graph, def.topics());
            return graph;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed curriculum definition", e);
        }
    }

    private static void validateDefinition(CurriculumDefinition def) {
        Set<String> ids = new HashSet<>();
        for (TopicDefinition td : def.topics()) {
            if (td.topicId() == null || td.topicId().trim().isEmpty()) {
                throw new IllegalArgumentException("Topic must have an ID");
            }
            if (!ids.add(td.topicId())) {
                throw new IllegalArgumentException("Duplicate topic ID: " + td.topicId());
            }
        }
        
        for (TopicDefinition td : def.topics()) {
            if (td.prerequisites() != null) {
                for (String prereq : td.prerequisites()) {
                    if (!ids.contains(prereq)) {
                        throw new IllegalArgumentException("Unknown prerequisite '" + prereq + "' in topic '" + td.topicId() + "'");
                    }
                }
            }
        }
    }

    private static void validateAcyclic(CurriculumGraph graph, List<TopicDefinition> topics) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        
        for (TopicDefinition td : topics) {
            if (!visited.contains(td.topicId())) {
                if (hasCycle(td.topicId(), graph, visiting, visited)) {
                    throw new IllegalArgumentException("Curriculum contains a prerequisite cycle");
                }
            }
        }
    }

    private static boolean hasCycle(String current, CurriculumGraph graph, Set<String> visiting, Set<String> visited) {
        visiting.add(current);
        
        for (String prereq : graph.getPrerequisites(current)) {
            if (prereq == null) continue;
            
            if (visiting.contains(prereq)) {
                return true; // Cycle detected
            }
            if (!visited.contains(prereq)) {
                if (hasCycle(prereq, graph, visiting, visited)) {
                    return true;
                }
            }
        }
        
        visiting.remove(current);
        visited.add(current);
        return false;
    }
}
