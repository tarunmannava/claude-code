import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;

import java.util.List;

public interface Model {
    AssistantTurn complete(Transcript transcript);

    record ToolCall(String id, String name, String arguments) {}

    record AssistantTurn(String content, List<ToolCall> toolCalls, ChatCompletionAssistantMessageParam message) {
        public AssistantTurn {
            content = content == null ? "" : content;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public static AssistantTurn text(String content) {
            return new AssistantTurn(
                    content,
                    List.of(),
                    ChatCompletionAssistantMessageParam.builder().content(content == null ? "" : content).build());
        }
    }
}
