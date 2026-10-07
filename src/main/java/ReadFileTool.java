import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@JsonClassDescription("Read and return the contents of a file")
public class ReadFileTool {

    @JsonPropertyDescription("The path to the file to read")
    @JsonProperty("file_path")
    public String filePath;

    public static String execute(String argumentsJson) throws IOException {
        ReadFileTool call = new ObjectMapper().readValue(argumentsJson, ReadFileTool.class);
        return Files.readString(Path.of(call.filePath));
    }
}
