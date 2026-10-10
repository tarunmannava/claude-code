[![progress-banner](https://backend.codecrafters.io/progress/claude-code/8aa6d22c-5906-48c2-85ed-15952156cd5a)](https://app.codecrafters.io/users/tarunmannava?r=2qF)

# Build Your Own Claude Code (Java)

This repository contains my Java solution to the [CodeCrafters "Build Your Own Claude Code" Challenge](https://codecrafters.io/challenges/claude-code), extended with an autonomous codebase migration engine, interactive terminal REPL, and modular agent architecture.

Claude Code is an AI coding assistant that uses Large Language Models (LLMs) to understand codebases, edit files, and execute shell commands through tool-calling loops. In this challenge, we build an agentic coding assistant from scratch in modern Java 17.

---

## 🏆 Challenge Stages Completed

### Core Stages
- [x] **Stage 1: Communicate with the LLM** — Configure the OpenAI-compatible HTTP client and handle initial prompt completions.
- [x] **Stage 2: Advertise the read tool** — Declare and pass JSON tool schemas for the `read` file operation.
- [x] **Stage 3: Execute the read tool** — Parse incoming tool calls, read files from the filesystem via `ReadFileTool`, and return tool results.
- [x] **Stage 4: Implement the agent loop** — Multi-turn conversation loop that feeds tool outputs back to the LLM until completion.
- [x] **Stage 5: Implement the write tool** — Added `WriteFileTool` (`write`) to allow the model to create and modify files with automatic parent directory creation.
- [x] **Stage 6: Implement the bash tool** — Added `BashTool` (`bash`) to execute shell commands using Java's `ProcessBuilder` (with cross-platform Windows `cmd.exe` and Linux `bash` support).

### Skills Extension ([Agent Skills Standard](https://agentskills.io/specification))
- [x] **Advertise skills to the LLM (Level 1)** — Scan `.claude/skills/`, parse `SKILL.md` YAML frontmatter, and summarize available skills in the system prompt.
- [x] **Invoke a skill by name (Level 2)** — Detect slash commands (e.g., `/<skill-name>`) and load only the invoked skill's body instructions on demand.
- [x] **Pass arguments to a skill** — Parse trailing inputs and perform template substitution for `$ARGUMENTS` and positional placeholders (`$0`, `$1`, ...).
- [x] **Stack multiple skills** — Expand chained slash commands (`/skill1 /skill2 args`), passing shared arguments to each invoked skill body across separate user messages.
- [x] **Run a script bundled with a skill** — Disclose skill folder paths (`Skill: <name> (located at <path>)`) so the model can resolve relative script references and execute them via `bash`.
- [x] **Let the model choose a skill** — Advertise the `Skill` tool (`SkillTool`), enabling the LLM to inspect skill descriptions in the system prompt and dynamically invoke matching skills.
- [x] **Run a skill in a subagent** — Execute skills configured with `context: fork` in an isolated subagent loop with fresh conversation context, returning synthesized results to the main agent.

### Advanced Capabilities
- [x] **Autonomous Codebase Migration (`/migrate`)** — Migrates full Python CRUD codebases to modern Java 17/Maven using AST dependency inspection, leaf-first topological translation, and compiler-in-the-loop self-healing.
- [x] **Interactive Terminal REPL** — Persistent shell session (`claude-code-java > `) supporting continuous multi-turn dialogue, slash commands, and natural-language tool calling.
- [x] **Deterministic Testing Harness** — Decoupled `Model` interface enabling 100% offline unit testing of agent loops with zero token spend via `ScriptedModel`.

---

## 🏗️ Architecture & Implementation

```text
src/main/java/
├── Main.java          # CLI entry point, REPL loop, and conversation runner
├── Agent.java         # Agentic multi-turn loop, iteration caps, and skill execution
├── Model.java         # Decoupled model interface and tool call abstractions
├── OpenAIModel.java   # OpenAI / OpenRouter client implementation of Model
├── Transcript.java    # Type-safe message history and tool request builder
├── Skill.java         # Skills loader, YAML frontmatter parser, and regex substitution
├── Env.java           # Environment variable resolver with .env fallback
└── tools/             # Tool definitions and execution engine
    ├── Tool.java          # Unified Tool interface and registry
    ├── ReadFileTool.java  # Tool definition and execution for reading files
    ├── WriteFileTool.java # Tool definition and execution for writing files
    ├── BashTool.java      # Cross-platform ProcessBuilder execution for shell commands
    └── SkillTool.java     # Tool definition and execution for dynamic model skill invocation
```

### Key Architectural Patterns

* **Decoupled Model Layer (`Model.java`, `OpenAIModel.java`)**: The core agent loop depends on the `Model` interface rather than concrete SDK clients, enabling instant, zero-cost unit testing with scripted fixtures.
* **Bounded Safety Controls (`Agent.java`)**:
  * `MAX_ITERATIONS = 25`: Circuit breaker preventing runaway token spend.
  * `MAX_SKILL_DEPTH = 1`: Enforces single-level subagent forks, preventing recursive fork loops.
* **Isolated Subagents (`context: fork`)**: When a skill requires heavy intermediate work (like multi-step migration and compilation), it runs in an isolated `Transcript`, discarding verbose compiler outputs and returning only the synthesized completion summary.
* **Deterministic AST Codebase Inspector (`.claude/skills/inspect-python/`)**: Uses Python's native `ast` module to extract classes, field types, routes, and dynamic hazards without executing source code.

---

## 🚀 Running Locally

### Prerequisites
* Java 17 LTS+
* Maven 3.8+
* Python 3.10+ (for Python AST inspection)

### Setup Environment
Configure your API key in the shell or place it in a `.env` file (automatically loaded by `Env.java` and git-ignored):

```properties
OPENROUTER_API_KEY=sk-or-v1-...
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
MODEL=anthropic/claude-haiku-5.5
```

### Build & Package
```bash
mvn clean package -DskipTests
```

### 1. Interactive Terminal REPL
Launch the persistent interactive assistant:

```bash
java -jar target/codecrafters-claude-code.jar
```
```text
==================================================================
  Claude Code (Java) - Conversational Agent (REPL)
  Available Skills:
    /inspect-python - Deterministically inspects a Python codebase...
    /migrate - Migrates a Python project to modern Java...
  Type /exit to quit.
==================================================================

claude-code-java > /migrate src/test/resources/fixtures/01-todo-crud target/migrated-todo
```

### 2. CodeCrafters CLI One-Shot Mode
Execute single prompts via the `-p` flag:

```bash
# General coding assistant prompt
java -jar target/codecrafters-claude-code.jar -p "Read the file app/main.js and explain what it does"

# Execute a bash command via the agent loop
java -jar target/codecrafters-claude-code.jar -p "List files using ls and delete any temp files"

# Invoke an Agent Skill
java -jar target/codecrafters-claude-code.jar -p "/migrate src/test/resources/fixtures/01-todo-crud target/output"
```

---

## 🧪 Testing

Run the full unit and integration test suite:

```bash
mvn test
```

### Included Tests:
* **`AgentLoopTest`**: Tests tool registration idempotency, iteration caps, and forked skill nesting limits using an in-memory `ScriptedModel`.
* **`SkillTest`**: Tests prompt parsing, single-pass argument substitution, stacking, and directory disclosure.
* **`ToolExecutionTest`**: Tests `read`, `write` (with nested directories), and `bash` (cross-platform).
* **`ThinSliceMigrationTest`**: End-to-end integration test validating Phase 0 AST inspection, leaf record generation, and live LLM translation against an independent oracle.

---

## 🧪 Testing with CodeCrafters

To submit progress to CodeCrafters:

```bash
codecrafters submit
```
