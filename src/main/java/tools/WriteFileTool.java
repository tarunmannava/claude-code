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
public class WriteFileTool implements Tool {

    @JsonProperty("file_path")
    public String filePath;

    @JsonProperty("content")
    public String content;

    @Override
    public String name() {
        return "write";
    }

    @Override
    public ChatCompletionTool toolDefinition() {
        return tool();
    }

    public static ChatCompletionTool tool() {
        return ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("write")
                        .description("Write content to a file")
                        .parameters(FunctionParameters.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("required", JsonValue.from(List.of("file_path", "content")))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "file_path", Map.of(
                                                "type", "string",
                                                "description", "The path of the file to write to"),
                                        "content", Map.of(
                                                "type", "string",
                                                "description", "The content to write to the file"))))
                                .build())
                        .build())
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            WriteFileTool call = new ObjectMapper().readValue(argumentsJson, WriteFileTool.class);
            Path path = Path.of(call.filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, call.content);
            return "File written successfully";
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }
}
