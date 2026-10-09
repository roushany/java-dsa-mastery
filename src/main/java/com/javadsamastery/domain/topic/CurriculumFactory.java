package com.javadsamastery.domain.topic;

/**
 * Factory to construct the official DSA curriculum dependency graph.
 */
public class CurriculumFactory {

    public static CurriculumGraph createStandardCurriculum() {
        return com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
    }
}
