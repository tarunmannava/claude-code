import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 2 || !"-p".equals(args[0])) {
            System.err.println("Usage: program -p <prompt>");
            System.exit(1);
        }

        String prompt = args[1];

        String apiKey = System.getenv("OPENROUTER_API_KEY");
        String baseUrl = System.getenv("OPENROUTER_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("OPENROUTER_API_KEY is not set");
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        ChatCompletionCreateParams.Builder messages = ChatCompletionCreateParams.builder()
                .model("anthropic/claude-haiku-4.5")
                .addUserMessage(prompt)
                .addTool(ReadFileTool.tool())
                .addTool(WriteFileTool.tool());

        while (true) {
            ChatCompletion response = client.chat().completions().create(messages.build());
            if (response.choices().isEmpty()) {
                throw new RuntimeException("no choices in response");
            }

            var message = response.choices().get(0).message();
            messages.addMessage(message);

            var toolCalls = message.toolCalls().orElse(List.of());
            if (toolCalls.isEmpty()) {
                System.out.print(message.content().orElse(""));
                break;
            }

            for (var toolCall : toolCalls) {
                if (!"read".equals(toolCall.function().name()) && !"write".equals(toolCall.function().name())) {
                    throw new RuntimeException("unknown tool: " + toolCall.function().name());
                }
                if("read".equals(toolCall.function().name())) {
                    messages.addMessage(ChatCompletionToolMessageParam.builder()
                            .toolCallId(toolCall.id())
                            .content(ReadFileTool.execute(toolCall.function().arguments()))
                            .build());
                } else if("write".equals(toolCall.function().name())) {
                    messages.addMessage(ChatCompletionToolMessageParam.builder()
                            .toolCallId(toolCall.id())
                            .content(WriteFileTool.execute(toolCall.function().arguments()))
                            .build());
                }
            }
        }

        System.err.println("Logs from your program will appear here!");
    }
}
