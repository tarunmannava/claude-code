import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import tools.SkillTool;
import tools.Tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        String apiKey = Env.get("OPENROUTER_API_KEY");
        String baseUrl = Env.get("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1");

        // Mode 1: One-shot prompt mode (-p <prompt>)
        if (args.length >= 2 && "-p".equals(args[0])) {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("OPENROUTER_API_KEY is not set");
            }
            OpenAIClient client = OpenAIOkHttpClient.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .build();
            runOneShot(client, args[1]);
            return;
        }

        // Mode 2: Interactive Terminal REPL
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: OPENROUTER_API_KEY environment variable is not set.");
            System.exit(1);
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        runRepl(client);
    }

    public static final String DEFAULT_MODEL = Env.get("MODEL", "anthropic/claude-haiku-5.5");

    private static void runRepl(OpenAIClient client) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<Skill> skills = Skill.loadAll();
        String skillsPrompt = Skill.formatSkillsPrompt(skills);
        List<Tool> tools = new ArrayList<>(Tool.defaultTools());
        if (!skills.isEmpty()) {
            tools.add(new SkillTool((name, skillArgs) -> executeSkill(client, skills, name, skillArgs)));
        }

        System.out.println("==================================================================");
        System.out.println("  Claude Code (Java) - Conversational Agent (REPL)");
        if (!skills.isEmpty()) {
            System.out.println("  Available Skills:");
            for (Skill s : skills) {
                System.out.println("    /" + s.name + " - " + s.description);
            }
        }
        System.out.println("  Type /exit to quit.");
        System.out.println("==================================================================");

        ChatCompletionCreateParams.Builder messages = ChatCompletionCreateParams.builder()
                .model(DEFAULT_MODEL);

        if (skillsPrompt != null && !skillsPrompt.isEmpty()) {
            messages.addSystemMessage(skillsPrompt);
        }

        while (true) {
            System.out.print("\nclaude-code-java > ");
            String line = reader.readLine();
            if (line == null || "/exit".equalsIgnoreCase(line.trim()) || "exit".equalsIgnoreCase(line.trim())) {
                System.out.println("Goodbye!");
                break;
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            Skill.ParsedPrompt parsed = Skill.parsePrompt(line, skills);
            if (!parsed.skills().isEmpty()) {
                for (Skill skill : parsed.skills()) {
                    if (skill.isFork()) {
                        try {
                            String response = executeSkill(client, skills, skill.name, parsed.argumentsText());
                            System.out.println("\n" + response);
                            messages.addUserMessage(line);
                            messages.addMessage(ChatCompletionAssistantMessageParam.builder()
                                    .content("Skill /" + skill.name + " executed: " + response)
                                    .build());
                        } catch (Exception e) {
                            System.err.println("Error running forked skill: " + e.getMessage());
                        }
                    } else {
                        messages.addUserMessage(skill.formatPrompt(parsed.argumentsText()));
                        try {
                            String response = runAgentLoop(client, messages, tools);
                            System.out.println("\n" + response);
                        } catch (Exception e) {
                            System.err.println("Error in agent loop: " + e.getMessage());
                        }
                    }
                }
            } else {
                messages.addUserMessage(line);
                try {
                    String response = runAgentLoop(client, messages, tools);
                    System.out.println("\n" + response);
                } catch (Exception e) {
                    System.err.println("Error in agent loop: " + e.getMessage());
                }
            }
        }
    }

    private static void runOneShot(OpenAIClient client, String prompt) {
        List<Skill> skills = Skill.loadAll();
        String skillsPrompt = Skill.formatSkillsPrompt(skills);
        List<Tool> tools = new ArrayList<>(Tool.defaultTools());
        if (!skills.isEmpty()) {
            tools.add(new SkillTool((name, skillArgs) -> executeSkill(client, skills, name, skillArgs)));
        }

        ChatCompletionCreateParams.Builder messages = ChatCompletionCreateParams.builder()
                .model(DEFAULT_MODEL);

        if (skillsPrompt != null && !skillsPrompt.isEmpty()) {
            messages.addSystemMessage(skillsPrompt);
        }

        Skill.ParsedPrompt parsed = Skill.parsePrompt(prompt, skills);
        if (!parsed.skills().isEmpty()) {
            for (Skill skill : parsed.skills()) {
                if (skill.isFork()) {
                    String result = executeSkill(client, skills, skill.name, parsed.argumentsText());
                    System.out.print(result);
                    System.err.println("Logs from your program will appear here!");
                    return;
                }
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
                    .model(DEFAULT_MODEL);
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

                String result = tool.execute(toolCall.function().arguments());
                messages.addMessage(ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id())
                        .content(result)
                        .build());
            }
        }
    }
}
