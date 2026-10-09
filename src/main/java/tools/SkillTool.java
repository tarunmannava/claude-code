package tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class SkillTool implements Tool {

    private final BiFunction<String, String, String> skillHandler;

    public SkillTool(BiFunction<String, String, String> skillHandler) {
        this.skillHandler = skillHandler;
    }

    @Override
    public String name() {
        return "Skill";
    }

    @Override
    public ChatCompletionTool toolDefinition() {
        return tool();
    }

    public static ChatCompletionTool tool() {
        return ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("Skill")
                        .description("Load a skill's instructions into the conversation")
                        .parameters(FunctionParameters.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("required", JsonValue.from(List.of("name")))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "name", Map.of(
                                                "type", "string",
                                                "description", "The name of the skill to use"),
                                        "args", Map.of(
                                                "type", "string",
                                                "description", "Optional arguments for the skill"))))
                                .build())
                        .build())
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillArgs {
        @JsonProperty("name")
        public String name;

        @JsonProperty("args")
        public String args;
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            SkillArgs call = new ObjectMapper().readValue(argumentsJson, SkillArgs.class);
            if (call.name == null || call.name.isBlank()) {
                return "Error: Skill name is required";
            }
            return skillHandler.apply(call.name, call.args);
        } catch (Exception e) {
            return "Error loading skill: " + e.getMessage();
        }
    }
}
