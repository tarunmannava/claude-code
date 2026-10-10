import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import tools.SkillTool;
import tools.Tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static final String DEFAULT_MODEL = Env.get("MODEL", "anthropic/claude-haiku-5.5");

    public static void main(String[] args) throws IOException {
        String apiKey = Env.get("OPENROUTER_API_KEY");
        String baseUrl = Env.get("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1");

        if (args.length >= 2 && "-p".equals(args[0])) {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("OPENROUTER_API_KEY is not set");
            }
            runOneShot(new OpenAIModel(client(apiKey, baseUrl)), args[1]);
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: OPENROUTER_API_KEY environment variable is not set.");
            System.exit(1);
        }

        runRepl(new OpenAIModel(client(apiKey, baseUrl)));
    }

    private static OpenAIClient client(String apiKey, String baseUrl) {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    private static void runRepl(Model model) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Conversation conversation = Conversation.start(model);

        System.out.println("==================================================================");
        System.out.println("  Claude Code (Java) - Conversational Agent (REPL)");
        if (!conversation.skills.isEmpty()) {
            System.out.println("  Available Skills:");
            for (Skill skill : conversation.skills) {
                System.out.println("    /" + skill.name + " - " + skill.description);
            }
        }
        System.out.println("  Type /exit to quit.");
        System.out.println("==================================================================");

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

            dispatch(conversation, line, true);
        }
    }

    private static void runOneShot(Model model, String prompt) {
        dispatch(Conversation.start(model), prompt, false);
        System.err.println("Logs from your program will appear here!");
    }

    private static void dispatch(Conversation conversation, String line, boolean repl) {
        Skill.ParsedPrompt parsed = Skill.parsePrompt(line, conversation.skills);
        if (parsed.skills().isEmpty()) {
            conversation.transcript.addUserMessage(line);
            reply(conversation, repl);
            return;
        }

        boolean inlineAdded = false;
        for (Skill skill : parsed.skills()) {
            if (skill.isFork()) {
                if (!repl && inlineAdded) {
                    emit(conversation.run(), false);
                }
                runFork(conversation, line, parsed.argumentsText(), skill, repl);
                return;
            }
            conversation.transcript.addUserMessage(skill.render(parsed.argumentsText()));
            inlineAdded = true;
            if (repl) {
                reply(conversation, true);
            }
        }
        if (!repl) {
            reply(conversation, false);
        }
    }

    private static void runFork(Conversation conversation, String line, String args, Skill skill, boolean repl) {
        try {
            String response = conversation.agent.executeSkill(conversation.skills, skill.name, args, 0);
            if (repl) {
                conversation.transcript.addUserMessage(line);
                conversation.transcript.addAssistantMessage("Skill /" + skill.name + " executed: " + response);
            }
            emit(response, repl);
        } catch (Exception e) {
            if (repl) {
                System.err.println("Error running forked skill: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private static void reply(Conversation conversation, boolean repl) {
        try {
            emit(conversation.run(), repl);
        } catch (Exception e) {
            if (repl) {
                System.err.println("Error in agent loop: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private static void emit(String response, boolean repl) {
        if (repl) {
            System.out.println("\n" + response);
        } else {
            System.out.print(response);
        }
    }

    private static final class Conversation {
        final Agent agent;
        final List<Skill> skills;
        final List<Tool> tools;
        final Transcript transcript;

        private Conversation(Agent agent, List<Skill> skills, List<Tool> tools, Transcript transcript) {
            this.agent = agent;
            this.skills = skills;
            this.tools = tools;
            this.transcript = transcript;
        }

        static Conversation start(Model model) {
            List<Skill> skills = Skill.loadAll();
            Agent agent = new Agent(model, DEFAULT_MODEL);
            Transcript transcript = new Transcript(DEFAULT_MODEL);
            String skillsPrompt = Skill.formatSkillsPrompt(skills);
            if (skillsPrompt != null && !skillsPrompt.isEmpty()) {
                transcript.addSystemMessage(skillsPrompt);
            }
            List<Tool> tools = new ArrayList<>(Tool.defaultTools());
            if (!skills.isEmpty()) {
                tools.add(new SkillTool((name, args) -> agent.executeSkill(skills, name, args, 0)));
            }
            return new Conversation(agent, skills, tools, transcript);
        }

        String run() {
            return agent.run(transcript, tools);
        }
    }
}
