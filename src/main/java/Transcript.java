import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import tools.Tool;

import java.util.ArrayList;
import java.util.List;

public final class Transcript {
    private final String model;
    private final List<ChatCompletionMessageParam> messages = new ArrayList<>();
    private final List<ChatCompletionTool> tools = new ArrayList<>();

    public Transcript(String model) {
        this.model = model;
    }

    public String model() {
        return model;
    }

    public void addSystemMessage(String content) {
        messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(content).build()));
    }

    public void addUserMessage(String content) {
        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(content).build()));
    }

    public void addAssistantMessage(String content) {
        messages.add(ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder().content(content).build()));
    }

    public void addAssistant(ChatCompletionAssistantMessageParam message) {
        messages.add(ChatCompletionMessageParam.ofAssistant(message));
    }

    public void addToolResult(String toolCallId, String content) {
        messages.add(ChatCompletionMessageParam.ofTool(
                ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCallId)
                        .content(content)
                        .build()));
    }

    public void setTools(List<Tool> toolList) {
        tools.clear();
        if (toolList == null) {
            return;
        }
        for (Tool tool : toolList) {
            tools.add(tool.toolDefinition());
        }
    }

    public int toolCount() {
        return tools.size();
    }

    public List<ChatCompletionMessageParam> messages() {
        return List.copyOf(messages);
    }

    public ChatCompletionCreateParams toRequest() {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder().model(model);
        for (ChatCompletionMessageParam message : messages) {
            builder.addMessage(message);
        }
        if (!tools.isEmpty()) {
            builder.tools(new ArrayList<>(tools));
        }
        return builder.build();
    }
}
