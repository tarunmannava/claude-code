package tools;

import com.openai.models.chat.completions.ChatCompletionTool;
import java.util.List;
import java.util.Optional;

public interface Tool {
    String name();

    ChatCompletionTool toolDefinition();

    String execute(String argumentsJson);

    static List<Tool> defaultTools() {
        return List.of(new ReadFileTool(), new BashTool(), new WriteFileTool());
    }

    static Optional<Tool> find(List<Tool> tools, String name) {
        if (tools == null || name == null) {
            return Optional.empty();
        }
        return tools.stream().filter(t -> t.name().equals(name)).findFirst();
    }
}
