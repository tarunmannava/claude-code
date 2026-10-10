package tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BashTool implements Tool {

    @JsonProperty("command")
    public String command;

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public ChatCompletionTool toolDefinition() {
        return tool();
    }

    public static ChatCompletionTool tool() {
        return ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("bash")
                        .description("Execute a shell command")
                        .parameters(FunctionParameters.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "command", Map.of(
                                                "type", "string",
                                                "description", "The command to execute"))))
                                .putAdditionalProperty("required", JsonValue.from(List.of("command")))
                                .build())
                        .build())
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            BashTool call = new ObjectMapper().readValue(argumentsJson, BashTool.class);

            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            ProcessBuilder processBuilder = isWindows
                    ? new ProcessBuilder("cmd.exe", "/c", call.command)
                    : new ProcessBuilder("bash", "-c", call.command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Read all output (stdout + stderr)
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            process.waitFor();
            return output;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
