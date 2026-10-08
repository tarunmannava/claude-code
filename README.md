[![progress-banner](https://backend.codecrafters.io/progress/claude-code/8aa6d22c-5906-48c2-85ed-15952156cd5a)](https://app.codecrafters.io/users/tarunmannava?r=2qF)

# Claude Code (Java)

A custom implementation of Anthropic's **Claude Code** agentic CLI built from scratch in Java, completed as part of the CodeCrafters ["Build Your Own Claude Code"](https://codecrafters.io/challenges/claude-code) challenge.

---

## Features

### 1. Autonomous Agent Loop & Tool Calling
* Connects to LLM endpoints (via OpenAI-compatible API / OpenRouter).
* Iterative agentic execution loop: receives tool calls from the model, executes them locally, feeds output back to the conversation, and repeats until the task is complete.

### 2. Built-in Tools
* **File Reading (`read`)**: Reads file content safely from the local filesystem using Java NIO (`ReadFileTool`).
* **File Writing (`write`)**: Writes and creates files on disk with specified contents (`WriteFileTool`).
* **Shell Command Execution (`bash`)**: Runs commands via `ProcessBuilder` in a shell environment, capturing both `stdout` and `stderr` (`BashTool`).

### 3. Agent Skills Extension ([Agent Skills Standard](https://agentskills.io/specification))
Supports user-defined skills placed in `.claude/skills/<skill-name>/SKILL.md`:
* **Level 1 Discovery (Progressive Disclosure)**: Scans available skills on startup, parses YAML frontmatter using SnakeYAML, and advertises their name and description in the system prompt without bloating the context window.
* **Level 2 Invocation**: Supports slash commands (e.g. `/apple`, `/nimbus`) to load only the invoked skill's body instructions on demand.
* **Template Parameterization**: Substitutes `$ARGUMENTS` with the full argument string, and resolves positional arguments (`$0`, `$1`, etc.) into the skill body.

---

## Project Structure

```text
.
├── .claude/
│   └── skills/          # Custom skill folders (each containing SKILL.md)
├── src/
│   └── main/
│       └── java/
│           ├── Main.java          # Entry point and agent conversation loop
│           ├── ReadFileTool.java  # Tool for reading files
│           ├── WriteFileTool.java # Tool for writing files
│           ├── BashTool.java      # Tool for executing shell commands
│           └── Skill.java         # Skills discovery, parsing & argument substitution
├── pom.xml                        # Maven configuration and dependencies
└── your_program.sh                # Local execution script
```

---

## Getting Started

### Prerequisites
* Java 25 (or compatible JDK)
* Maven 3.8+

### Environment Setup
Set your API key before running:

```bash
export OPENROUTER_API_KEY="your-api-key-here"
# Optional (defaults to https://openrouter.ai/api/v1):
# export OPENROUTER_BASE_URL="https://openrouter.ai/api/v1"
```

### Running Locally

```bash
# Run with a general prompt
./your_program.sh -p "List files in the current directory and summarize them"

# Invoke a skill with arguments
./your_program.sh -p "/deploy staging us-east"
```

---

## Submitting to CodeCrafters

To validate progress against CodeCrafters tests:

```bash
codecrafters submit
```
