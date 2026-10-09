package com.javadsamastery.ai.dto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
public class EvaluationResultDtoJacksonTest {
    @Test
    public void testDeserialization() throws Exception {
        String json = "{\"evaluation\":\"PASS\",\"confidenceScore\":100,\"misconceptionCategory\":null,\"feedback\":\"Great\",\"recommendedRemediation\":\"none\"}";
        ObjectMapper mapper = new ObjectMapper();
        EvaluationResultDto dto = mapper.readValue(json, EvaluationResultDto.class);
        System.out.println(dto.evaluation());
    }
}
