import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import org.junit.jupiter.api.Test;
import tools.ReadFileTool;
import tools.Tool;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopTest {
    @Test
    void twoTurnsRegisterToolsOnceAndReportUnknownTools() {
        ScriptedModel model = new ScriptedModel();
        model.enqueue(toolCall("call-1", "missing", "{}"));
        model.enqueue(Model.AssistantTurn.text("done"));

        Transcript transcript = new Transcript("test-model");
        transcript.addUserMessage("first");
        Agent agent = new Agent(model, "test-model", 5, 1);
        List<Tool> tools = List.of(new ReadFileTool());

        assertEquals("done", agent.run(transcript, tools));

        transcript.addUserMessage("second");
        model.enqueue(Model.AssistantTurn.text("still here"));
        assertEquals("still here", agent.run(transcript, tools));

        assertEquals(List.of(1, 1, 1), model.toolCounts);
        assertEquals(List.of(1, 1, 1), model.requestToolCounts);
        assertTrue(toolResults(transcript).contains("Error: unknown tool: missing"));
    }

    @Test
    void loopStopsAtTheIterationCap() {
        ScriptedModel model = new ScriptedModel();
        model.enqueue(toolCall("c1", "missing", "{}"));
        model.enqueue(toolCall("c2", "missing", "{}"));
        model.enqueue(toolCall("c3", "missing", "{}"));

        Transcript transcript = new Transcript("test-model");
        transcript.addUserMessage("loop");
        Agent agent = new Agent(model, "test-model", 3, 1);

        assertEquals(
                "Error: agent exceeded 3 iterations",
                agent.run(transcript, List.of(new ReadFileTool())));
        assertEquals(3, model.toolCounts.size());
        assertEquals(3, toolResults(transcript).lines().filter(r -> r.startsWith("Error: unknown tool:")).count());
    }

    @Test
    void slashAndToolUseTheSameSkillExecution() {
        Skill inline = new Skill("inspect", "Inspect", "Look at $0", Path.of("skills", "inspect"));
        Skill fork = new Skill("migrate", "Migrate", "Translate $0", Path.of("skills", "migrate"), "fork");
        List<Skill> skills = List.of(inline, fork);

        ScriptedModel model = new ScriptedModel();
        model.enqueue(Model.AssistantTurn.text("migrated"));
        Agent agent = new Agent(model, "test-model", 5, 1);

        assertEquals(
                "Skill: inspect (located at skills/inspect)\n"
                        + "Paths in the instructions below are relative to that folder.\n\n"
                        + "Look at src",
                agent.executeSkill(skills, "inspect", "src", 0));
        assertEquals(
                "Skill migrate ran in a separate context and returned: migrated",
                agent.executeSkill(skills, "migrate", "src", 0));
        assertEquals(List.of(3), model.toolCounts);
        assertEquals("Error: skill nesting limit exceeded", agent.executeSkill(skills, "migrate", "src", 1));
        assertEquals(1, model.toolCounts.size());
    }

    private static String toolResults(Transcript transcript) {
        StringBuilder results = new StringBuilder();
        for (ChatCompletionMessageParam message : transcript.messages()) {
            if (message.isTool()) {
                results.append(message.asTool().content().text().orElse("")).append('\n');
            }
        }
        return results.toString();
    }

    private static Model.AssistantTurn toolCall(String id, String name, String arguments) {
        ChatCompletionMessageToolCall call = ChatCompletionMessageToolCall.builder()
                .id(id)
                .function(ChatCompletionMessageToolCall.Function.builder()
                        .name(name)
                        .arguments(arguments)
                        .build())
                .build();
        ChatCompletionAssistantMessageParam message = ChatCompletionAssistantMessageParam.builder()
                .addToolCall(call)
                .build();
        return new Model.AssistantTurn("", List.of(new Model.ToolCall(id, name, arguments)), message);
    }

    private static final class ScriptedModel implements Model {
        final Queue<AssistantTurn> script = new ArrayDeque<>();
        final List<Integer> toolCounts = new ArrayList<>();
        final List<Integer> requestToolCounts = new ArrayList<>();

        void enqueue(AssistantTurn turn) {
            script.add(turn);
        }

        @Override
        public AssistantTurn complete(Transcript transcript) {
            toolCounts.add(transcript.toolCount());
            requestToolCounts.add(transcript.toRequest().tools().orElse(List.of()).size());
            if (script.isEmpty()) {
                throw new AssertionError("no scripted turn left");
            }
            return script.remove();
        }
    }
}
