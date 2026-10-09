package tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadFileTool implements Tool {

    @JsonProperty("file_path")
    public String filePath;

    @Override
    public String name() {
        return "read";
    }

    @Override
    public ChatCompletionTool toolDefinition() {
        return tool();
    }

    public static ChatCompletionTool tool() {
        return ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("read")
                        .description("Read and return the contents of a file")
                        .parameters(FunctionParameters.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "file_path", Map.of(
                                                "type", "string",
                                                "description", "The path to the file to read"))))
                                .putAdditionalProperty("required", JsonValue.from(List.of("file_path")))
                                .build())
                        .build())
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            ReadFileTool call = new ObjectMapper().readValue(argumentsJson, ReadFileTool.class);
            Path path = Path.of(call.filePath);
            if (Files.isDirectory(path)) {
                return "Error: " + call.filePath + " is a directory";
            }
            return Files.readString(path);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
