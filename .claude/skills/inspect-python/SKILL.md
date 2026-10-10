---
name: inspect-python
description: Deterministically inspects a Python codebase using AST analysis to extract imports, dependencies, models, routes, and dynamic hazards without executing code.
---

## When to Use This Skill
Use this skill when you need to understand the structure of a Python repository, map its internal dependencies, extract data models/schemas, discover HTTP routes, or find dynamic constructs.

## How to Execute
Run the bundled script `scripts/inspect_codebase.py` via the `bash` tool.

```bash
python scripts/inspect_codebase.py <source_dir> [--mode <mode>]
```

### Available Modes:
* `--mode all` (Default): Returns complete profile of files, imports, classes, functions, and dynamic hazards.
* `--mode imports`: Returns only imports and module references (useful for building dependency graphs).
* `--mode types`: Returns only class definitions, base classes, and field annotations (useful for DTOs and database models).
* `--mode routes`: Returns only functions decorated with HTTP route endpoints (e.g. `@app.get`, `@router.post`).
* `--mode hazards`: Scans specifically for dynamic Python constructs (`getattr`, `setattr`, `eval`, `exec`).

### Example Tool Invocation:
To analyze routes in a project:
```json
{
  "command": "python scripts/inspect_codebase.py ./my-python-app --mode routes"
}
```

Parse the returned JSON to answer the user's questions or structure your migration steps.
