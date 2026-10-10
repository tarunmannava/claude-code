import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;

import java.util.ArrayList;
import java.util.List;

public final class OpenAIModel implements Model {
    private final OpenAIClient client;

    public OpenAIModel(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public AssistantTurn complete(Transcript transcript) {
        ChatCompletion response = client.chat().completions().create(transcript.toRequest());
        if (response.choices().isEmpty()) {
            throw new RuntimeException("no choices in response");
        }

        ChatCompletionMessage message = response.choices().get(0).message();
        List<ToolCall> calls = new ArrayList<>();
        for (ChatCompletionMessageToolCall call : message.toolCalls().orElse(List.of())) {
            calls.add(new ToolCall(call.id(), call.function().name(), call.function().arguments()));
        }
        return new AssistantTurn(message.content().orElse(""), calls, message.toParam());
    }
}
