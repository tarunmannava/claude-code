import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadFileTool {

    @JsonProperty("file_path")
    public String filePath;

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

    public static String execute(String argumentsJson) {
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
