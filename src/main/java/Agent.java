import tools.SkillTool;
import tools.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Agent {
    public static final int MAX_ITERATIONS = 25;
    public static final int MAX_SKILL_DEPTH = 1;

    private final Model model;
    private final String modelName;
    private final int maxIterations;
    private final int maxSkillDepth;

    public Agent(Model model, String modelName) {
        this(model, modelName, MAX_ITERATIONS, MAX_SKILL_DEPTH);
    }

    public Agent(Model model, String modelName, int maxIterations, int maxSkillDepth) {
        this.model = model;
        this.modelName = modelName;
        this.maxIterations = maxIterations;
        this.maxSkillDepth = maxSkillDepth;
    }

    public String run(Transcript transcript, List<Tool> tools) {
        transcript.setTools(tools);
        for (int i = 0; i < maxIterations; i++) {
            Model.AssistantTurn turn = model.complete(transcript);
            transcript.addAssistant(turn.message());
            if (turn.toolCalls().isEmpty()) {
                return turn.content();
            }
            for (Model.ToolCall call : turn.toolCalls()) {
                transcript.addToolResult(call.id(), execute(tools, call));
            }
        }
        String stop = "Error: agent exceeded " + maxIterations + " iterations";
        transcript.addAssistantMessage(stop);
        return stop;
    }

    public String executeSkill(List<Skill> skills, String name, String args, int depth) {
        if (depth >= maxSkillDepth) {
            return "Error: skill nesting limit exceeded";
        }
        Skill skill = Skill.find(skills, name);
        if (skill == null) {
            return "Error: Skill not found: " + name;
        }

        String instructions = skill.render(args);
        if (!skill.isFork()) {
            return instructions;
        }

        Transcript subagent = new Transcript(modelName);
        subagent.addUserMessage(instructions);
        String answer = run(subagent, toolsForDepth(skills, depth + 1));
        return "Skill " + skill.name + " ran in a separate context and returned: " + answer.strip();
    }

    private List<Tool> toolsForDepth(List<Skill> skills, int depth) {
        List<Tool> tools = new ArrayList<>(Tool.defaultTools());
        if (depth < maxSkillDepth && skills != null && !skills.isEmpty()) {
            tools.add(new SkillTool((name, args) -> executeSkill(skills, name, args, depth)));
        }
        return tools;
    }

    private static String execute(List<Tool> tools, Model.ToolCall call) {
        Optional<Tool> tool = Tool.find(tools, call.name());
        if (tool.isEmpty()) {
            return "Error: unknown tool: " + call.name();
        }
        try {
            String result = tool.get().execute(call.arguments());
            return result != null ? result : "";
        } catch (Exception e) {
            return "Error executing tool: " + e.getMessage();
        }
    }
}
