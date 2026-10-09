import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import tools.SkillTool;
import tools.Tool;

import java.io.IOException;
import java.util.ArrayList;
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
        List<Skill> skills = Skill.loadAll();
        String skillsPrompt = Skill.formatSkillsPrompt(skills);
        List<Tool> tools = new ArrayList<>(Tool.defaultTools());
        if (!skills.isEmpty()) {
            tools.add(new SkillTool((name, skillArgs) -> executeSkill(client, skills, name, skillArgs)));
        }

        ChatCompletionCreateParams.Builder messages = ChatCompletionCreateParams.builder()
                .model("anthropic/claude-haiku-4.5");

        if (skillsPrompt != null && !skillsPrompt.isEmpty()) {
            messages.addSystemMessage(skillsPrompt);
        }

        Skill.ParsedPrompt parsed = Skill.parsePrompt(prompt, skills);
        if (!parsed.skills().isEmpty()) {
            for (Skill skill : parsed.skills()) {
                messages.addUserMessage(skill.formatPrompt(parsed.argumentsText()));
            }
        } else {
            messages.addUserMessage(prompt);
        }

        String finalResponse = runAgentLoop(client, messages, tools);
        System.out.print(finalResponse);

        System.err.println("Logs from your program will appear here!");
    }

    public static String executeSkill(OpenAIClient client, List<Skill> skills, String name, String args) {
        Skill skill = Skill.find(skills, name);
        if (skill == null) {
            return "Error: Skill not found: " + name;
        }

        if (skill.isFork()) {
            String subagentPrompt = skill.getInstructions(args);
            ChatCompletionCreateParams.Builder subMessages = ChatCompletionCreateParams.builder()
                    .model("anthropic/claude-haiku-4.5");
            subMessages.addUserMessage(subagentPrompt);
            String subagentAnswer = runAgentLoop(client, subMessages, Tool.defaultTools());
            return "Skill " + skill.name + " ran in a separate context and returned: " + subagentAnswer.strip();
        }

        return skill.getInstructions(args);
    }

    public static String runAgentLoop(OpenAIClient client,
                                      ChatCompletionCreateParams.Builder messages,
                                      List<Tool> tools) {
        for (Tool tool : tools) {
            messages.addTool(tool.toolDefinition());
        }

        while (true) {
            ChatCompletion response = client.chat().completions().create(messages.build());
            if (response.choices().isEmpty()) {
                throw new RuntimeException("no choices in response");
            }

            var message = response.choices().get(0).message();
            messages.addMessage(message);

            var toolCalls = message.toolCalls().orElse(List.of());
            if (toolCalls.isEmpty()) {
                return message.content().orElse("");
            }

            for (var toolCall : toolCalls) {
                String toolName = toolCall.function().name();
                Tool tool = Tool.find(tools, toolName)
                        .orElseThrow(() -> new RuntimeException("unknown tool: " + toolName));

                messages.addMessage(ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id())
                        .content(tool.execute(toolCall.function().arguments()))
                        .build());
            }
        }
    }
}
