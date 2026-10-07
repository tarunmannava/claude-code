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

public class WriteFileTool {

    @JsonProperty("file_path")
    public String filePath;

    @JsonProperty("content")
    public String content;

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

    public static String execute(String argumentsJson) throws IOException {
        WriteFileTool call = new ObjectMapper().readValue(argumentsJson, WriteFileTool.class);
        Files.writeString(Path.of(call.filePath), call.content);
        return "File written successfully";
    }
}
