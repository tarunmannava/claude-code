---
name: migrate
description: Migrates a Python project to a modern Java project using AST inspection, leaf-first topological translation, and compiler-in-the-loop repair.
context: fork
---

## Migration Workflow Instructions
You are an expert autonomous software engineer migrating a Python codebase to modern Java 17 (Maven).
Arguments provided:
- Source Python directory: `$0`
- Target Java directory: `$1`

Follow this structured protocol:

### Phase 1: Codebase Inspection
First, discover the repository structure, imports, classes, and routes.
Call the `bash` tool to run the AST inspector on the source directory:
```bash
python .claude/skills/inspect-python/scripts/inspect_codebase.py $0 --mode all
```
Analyze the returned JSON:
* Identify the **Leaf Nodes** (files with zero internal dependencies, like models and utilities).
* Identify any circular dependencies or dynamic Python hazards (`getattr`, `setattr`).
* Compute the topological order of files to translate (leaves first).

### Phase 2: Target Project Scaffolding
Use the `write` tool to scaffold the target project in the target directory (`$1`):
1. Create `$1/pom.xml` configured for Java 17 with JUnit 5 Jupiter.
2. Create package directories under `$1/src/main/java/` and `$1/src/test/java/`.

### Phase 3: Leaf-First Translation
Translate files following the dependency order (leaves first):
1. **Models & Entities**: Map Python Pydantic models / dataclasses to Java 17 `record`s under `$1/src/main/java/...`.
2. **Utilities & Repositories**: Translate data access and helper functions.
3. **Services & Controllers**: Translate business logic and API endpoints.
4. **Unit Tests**: Translate Python tests to JUnit 5 `@Test` classes under `$1/src/test/java/...`.

### Phase 4: Compiler-in-the-Loop Self-Healing
Verify the build using the `bash` tool:
```bash
mvn -f $1/pom.xml test-compile
```
If there are compilation errors:
* Inspect the line numbers and error diagnostics from the compiler output.
* Use the `write` tool to correct the Java files.
* Re-run `mvn -f $1/pom.xml test-compile` until the build succeeds.

### Phase 5: Test Parity Verification
Execute the test suite using the `bash` tool:
```bash
mvn -f $1/pom.xml test
```
Confirm that all tests pass with exit code 0.

Once complete, output a clean, concise summary of the migrated files, created tests, and build status.
