[![progress-banner](https://backend.codecrafters.io/progress/claude-code/8aa6d22c-5906-48c2-85ed-15952156cd5a)](https://app.codecrafters.io/users/tarunmannava?r=2qF)

# Build Your Own Claude Code (Java)

This repository contains my Java solution to the [CodeCrafters "Build Your Own Claude Code" Challenge](https://codecrafters.io/challenges/claude-code).

Claude Code is an AI coding assistant that uses Large Language Models (LLMs) to understand codebases, edit files, and execute shell commands through tool-calling loops. In this challenge, we build an agentic coding assistant from scratch in Java.

---

## 🏆 Challenge Stages Completed

### Core Stages
- [x] **Stage 1: Communicate with the LLM** — Configure the OpenAI-compatible HTTP client and handle initial prompt completions.
- [x] **Stage 2: Advertise the read tool** — Declare and pass JSON tool schemas for the `read` file operation.
- [x] **Stage 3: Execute the read tool** — Parse incoming tool calls, read files from the filesystem via `ReadFileTool`, and return tool results.
- [x] **Stage 4: Implement the agent loop** — Multi-turn conversation loop that feeds tool outputs back to the LLM until completion.
- [x] **Stage 5: Implement the write tool** — Added `WriteFileTool` (`write`) to allow the model to create and modify files.
- [x] **Stage 6: Implement the bash tool** — Added `BashTool` (`bash`) to execute shell commands using Java's `ProcessBuilder` and capture combined `stdout`/`stderr`.

### Skills Extension ([Agent Skills Standard](https://agentskills.io/specification))
- [x] **Advertise skills to the LLM (Level 1)** — Scan `.claude/skills/`, parse `SKILL.md` YAML frontmatter, and summarize available skills in the system prompt.
- [x] **Invoke a skill by name (Level 2)** — Detect slash commands (e.g., `/<skill-name>`) and load only the invoked skill's body instructions on demand.
- [x] **Pass arguments to a skill** — Parse trailing inputs and perform template substitution for `$ARGUMENTS` and positional placeholders (`$0`, `$1`, ...).
- [ ] **Stack multiple skills**
- [ ] **Run a script bundled with a skill**
- [ ] **Let the model choose a skill**
- [ ] **Run a skill in a subagent**

---

## 🏗️ Architecture & Implementation

```text
src/main/java/
├── Main.java          # Entry point, CLI argument parsing, and agentic loop
├── ReadFileTool.java  # Tool definition and execution for reading files
├── WriteFileTool.java # Tool definition and execution for writing files
├── BashTool.java      # Tool definition and ProcessBuilder execution for bash commands
└── Skill.java         # Skills loader, YAML frontmatter parser, and argument substituter
```

### Key Components

* **OpenAI-Compatible Tool Calling (`Main.java`)**: Uses `openai-java` with OpenRouter to stream messages and execute function tool calls dynamically in a `while(true)` agent loop.
* **Process Execution (`BashTool.java`)**: Spawns sub-processes with `ProcessBuilder`, redirecting error streams to capture unified execution logs without deadlocks.
* **Agent Skills Loader (`Skill.java`)**: Implements progressive disclosure. Loads Level 1 metadata via `SnakeYAML` for cataloging, and resolves Level 2 markdown bodies when invoked by slash commands.

---

## 🚀 Running Locally

### Prerequisites
* Java 25 (or compatible JDK)
* Maven 3.8+

### Setup Environment
Set your OpenRouter API key:

```bash
export OPENROUTER_API_KEY="your-api-key"
# Optional (defaults to https://openrouter.ai/api/v1):
# export OPENROUTER_BASE_URL="https://openrouter.ai/api/v1"
```

### Run
Execute the program via the CodeCrafters runner script:

```bash
# General coding assistant prompt
./your_program.sh -p "Read the file app/main.js and explain what it does"

# Execute a bash command via the agent loop
./your_program.sh -p "List files using ls and delete any temp files"

# Invoke an Agent Skill
./your_program.sh -p "/deploy staging us-west"
```

---

## 🧪 Testing with CodeCrafters

To run remote tests and submit progress to CodeCrafters:

```bash
codecrafters submit
```
