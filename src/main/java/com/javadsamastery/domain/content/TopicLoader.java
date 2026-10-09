package com.javadsamastery.domain.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

public class TopicLoader {
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static TopicTemplate load(String topicId) {
        if (topicId == null || topicId.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid topic identifier: topicId cannot be null or empty.");
        }

        String resourcePath = "/topics/" + topicId + ".json";
        
        try (InputStream is = TopicLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Topic resource not found: " + topicId);
            }
            
            TopicTemplate template = mapper.readValue(is, TopicTemplate.class);
            validate(template);
            return template;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed content or failed to parse topic: " + topicId, e);
        }
    }

    private static void validate(TopicTemplate template) {
        if (template.topicId() == null || template.topicId().trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: topicId");
        }
        if (template.title() == null || template.title().trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: title");
        }
        if (template.learningObjectives() == null || template.learningObjectives().trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: learningObjectives");
        }
        // Ensure that the loaded ID matches the expected structural constraints if necessary
    }
}
