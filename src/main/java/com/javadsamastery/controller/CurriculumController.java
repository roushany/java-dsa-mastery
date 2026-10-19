package com.javadsamastery.controller;

import com.javadsamastery.domain.curriculum.CurriculumDefinition;
import com.javadsamastery.domain.curriculum.CurriculumLoader;
import com.javadsamastery.domain.curriculum.TopicDefinition;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.dto.CurriculumResponse;
import com.javadsamastery.dto.TopicSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class CurriculumController {

    private final CurriculumGraph curriculumGraph;
    private static final Set<String> IMPLEMENTED_TOPICS = Set.of(
            "linear-search",
            "binary-search",
            "two-pointers",
            "sliding-window",
            "prefix-sum",
            "hashing",
            "binary-search-on-answer"
    );

    public CurriculumController(CurriculumGraph curriculumGraph) {
        this.curriculumGraph = curriculumGraph;
    }

    @GetMapping("/curriculum")
    public ResponseEntity<CurriculumResponse> getCurriculum() {
        List<TopicSummaryDto> dtos = new ArrayList<>();

        for (String topicId : IMPLEMENTED_TOPICS) {
            TopicDefinition def = curriculumGraph.getDefinition(topicId);
            if (def != null) {
                int stageCount = def.stageRequirements() != null ? def.stageRequirements().size() : 9;
                dtos.add(new TopicSummaryDto(
                        def.topicId(),
                        def.title(),
                        def.difficulty(),
                        def.prerequisites() != null ? def.prerequisites() : List.of(),
                        true,
                        stageCount
                ));
            }
        }

        return ResponseEntity.ok(new CurriculumResponse(dtos));
    }
}
