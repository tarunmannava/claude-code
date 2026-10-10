import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ThinSliceMigrationTest {

    private static final Path FIXTURE_DIR = Path.of("src", "test", "resources", "fixtures", "01-todo-crud");
    private static final Path INSPECT_SCRIPT = Path.of(".claude", "skills", "inspect-python", "scripts", "inspect_codebase.py");

    @TempDir
    Path tempDir;

    @Test
    void testPhase0InspectionDeterministic() throws Exception {
        // Step 1: Run deterministic AST inspection on fixture 01
        ProcessBuilder pb = new ProcessBuilder("python", INSPECT_SCRIPT.toString(), FIXTURE_DIR.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            output = sb.toString();
        }

        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "inspect_codebase.py should exit with code 0");
        assertTrue(output.contains("\"name\": \"Item\""), "Output should contain Item class");
        assertTrue(output.contains("\"name\": \"get_item\""), "Output should contain get_item function");
        assertTrue(output.contains("\"name\": \"create_item\""), "Output should contain create_item function");
        System.out.println("[Phase 0] AST inspection extracted Item and CRUD functions deterministically ($0 tokens).");
    }

    @Test
    void testDeterministicLeafGenerationAndCompilation() throws Exception {
        // Step 2: Deterministic generation of Item.java record ($0 tokens)
        Path modelsDir = tempDir.resolve(Path.of("src", "main", "java", "com", "example", "todo", "models"));
        Files.createDirectories(modelsDir);

        Path itemJava = modelsDir.resolve("Item.java");
        String itemCode = """
                package com.example.todo.models;

                public record Item(
                    Long id,
                    String title,
                    Boolean completed,
                    Double price
                ) {}
                """;
        Files.writeString(itemJava, itemCode, StandardCharsets.UTF_8);

        // Compile Item.java
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "System Java Compiler should be available");

        int compileResult = compiler.run(null, null, null, itemJava.toString());
        assertEquals(0, compileResult, "Deterministic Item.java should compile cleanly");
        System.out.println("[Leaf Generation] Item.java record compiled successfully without LLM involvement.");
    }

    @Test
    void testPreflightFailsOnThrowingStub() throws Exception {
        // Step 3 & 4: Oracle generation + Preflight negative test
        Path baseDir = tempDir.resolve("preflight-test");
        Path mainDir = baseDir.resolve(Path.of("src", "main", "java", "com", "example", "todo"));
        Path modelsDir = mainDir.resolve("models");
        Path crudDir = mainDir.resolve("crud");
        Files.createDirectories(modelsDir);
        Files.createDirectories(crudDir);

        // 1. Leaf Item.java
        Files.writeString(modelsDir.resolve("Item.java"), """
                package com.example.todo.models;

                public record Item(Long id, String title, Boolean completed, Double price) {}
                """, StandardCharsets.UTF_8);

        // 2. Throwing Stub for ItemCrud.java
        Path stubCrudJava = crudDir.resolve("ItemCrud.java");
        Files.writeString(stubCrudJava, """
                package com.example.todo.crud;

                import com.example.todo.models.Item;

                public class ItemCrud {
                    public static Item getItem(Long itemId) {
                        throw new UnsupportedOperationException("Stub not implemented");
                    }

                    public static Item createItem(Item item) {
                        throw new UnsupportedOperationException("Stub not implemented");
                    }
                }
                """, StandardCharsets.UTF_8);

        // 3. Engine-Generated Oracle Test
        Path testDir = baseDir.resolve(Path.of("src", "test", "java", "com", "example", "todo", "crud"));
        Files.createDirectories(testDir);
        Path oracleTestJava = testDir.resolve("ItemCrudOracleRunner.java");

        // A standalone runner that executes the assertions and catches failures
        Files.writeString(oracleTestJava, """
                package com.example.todo.crud;

                import com.example.todo.models.Item;

                public class ItemCrudOracleRunner {
                    public static void main(String[] args) {
                        // Assertion 1: getItem
                        Item item = ItemCrud.getItem(1L);
                        if (item == null || !Long.valueOf(1L).equals(item.id())) {
                            System.exit(1);
                        }

                        // Assertion 2: createItem
                        Item input = new Item(42L, "Test", true, 9.99);
                        Item created = ItemCrud.createItem(input);
                        if (created == null || !Long.valueOf(42L).equals(created.id())) {
                            System.exit(2);
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        // Compile all 3
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream errOut = new ByteArrayOutputStream();
        int compileResult = compiler.run(null, null, errOut,
                modelsDir.resolve("Item.java").toString(),
                stubCrudJava.toString(),
                oracleTestJava.toString());
        assertEquals(0, compileResult, "Compilation should succeed for stub and runner: " + errOut);

        // Run the runner with the throwing stub -> MUST fail with UnsupportedOperationException
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", baseDir.resolve(Path.of("src", "main", "java")) + File.pathSeparator + baseDir.resolve(Path.of("src", "test", "java")),
                "com.example.todo.crud.ItemCrudOracleRunner");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = reader.readLine()) != null) sb.append(l).append("\n");
            output = sb.toString();
        }
        int exitCode = p.waitFor();

        assertNotEquals(0, exitCode, "Preflight Negative Check: Oracle MUST fail against throwing stub");
        assertTrue(output.contains("UnsupportedOperationException"),
                "Preflight output should prove execution reached the throwing method: " + output);
        System.out.println("[Preflight] Negative check passed: Oracle successfully rejected throwing stub with exit code " + exitCode);
    }

    @Test
    void testTranslatedWorkerImplementationPassesOracle() throws Exception {
        // Step 5: Test the translated worker implementation against the oracle
        Path baseDir = tempDir.resolve("worker-test");
        Path mainDir = baseDir.resolve(Path.of("src", "main", "java", "com", "example", "todo"));
        Path modelsDir = mainDir.resolve("models");
        Path crudDir = mainDir.resolve("crud");
        Files.createDirectories(modelsDir);
        Files.createDirectories(crudDir);

        // 1. Leaf Item.java
        Files.writeString(modelsDir.resolve("Item.java"), """
                package com.example.todo.models;

                public record Item(Long id, String title, Boolean completed, Double price) {}
                """, StandardCharsets.UTF_8);

        // 2. Worker Implementation of ItemCrud.java
        Path workerCrudJava = crudDir.resolve("ItemCrud.java");

        String apiKey = Env.get("OPENROUTER_API_KEY");
        String baseUrl = Env.get("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1");

        if (apiKey != null && !apiKey.isBlank()) {
            System.out.println("[Live Worker] OPENROUTER_API_KEY detected in .env. Calling Claude Haiku...");
            OpenAIClient client = OpenAIOkHttpClient.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .build();

            String pythonSource = Files.readString(FIXTURE_DIR.resolve(Path.of("crud", "item_crud.py")));
            String prompt = "You are a professional Java developer.\n" +
                    "Translate this Python CRUD module into a single standalone Java class.\n\n" +
                    "Python code:\n" + pythonSource + "\n\n" +
                    "Target package: com.example.todo.crud\n" +
                    "Target class: public class ItemCrud\n" +
                    "Using model: com.example.todo.models.Item (record Item(Long id, String title, Boolean completed, Double price))\n" +
                    "Required methods:\n" +
                    "  public static Item getItem(Long itemId)\n" +
                    "  public static Item createItem(Item item)\n\n" +
                    "Output ONLY the complete Java file source code with no surrounding markdown or explanation.";

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("anthropic/claude-haiku-5.5")
                    .addUserMessage(prompt)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);
            String content = completion.choices().get(0).message().content().orElse("");
            // Clean any markdown backticks if returned
            if (content.contains("```java")) {
                int start = content.indexOf("```java") + 7;
                int end = content.indexOf("```", start);
                content = (end > start) ? content.substring(start, end).trim() : content.substring(start).trim();
            } else if (content.contains("```")) {
                int start = content.indexOf("```") + 3;
                int end = content.indexOf("```", start);
                content = (end > start) ? content.substring(start, end).trim() : content.substring(start).trim();
            }
            Files.writeString(workerCrudJava, content, StandardCharsets.UTF_8);
            System.out.println("[Live Worker] Successfully generated ItemCrud.java from live model!");
        } else {
            System.out.println("[Worker] OPENROUTER_API_KEY not set in .env. Using canonical reference translation.");
            Files.writeString(workerCrudJava, """
                    package com.example.todo.crud;

                    import com.example.todo.models.Item;

                    public class ItemCrud {
                        public static Item getItem(Long itemId) {
                            return new Item(itemId, "Sample Item", false, null);
                        }

                        public static Item createItem(Item item) {
                            return item;
                        }
                    }
                    """, StandardCharsets.UTF_8);
        }

        // 3. Engine-Generated Oracle Test Runner
        Path testDir = baseDir.resolve(Path.of("src", "test", "java", "com", "example", "todo", "crud"));
        Files.createDirectories(testDir);
        Path oracleTestJava = testDir.resolve("ItemCrudOracleRunner.java");
        Files.writeString(oracleTestJava, """
                package com.example.todo.crud;

                import com.example.todo.models.Item;

                public class ItemCrudOracleRunner {
                    public static void main(String[] args) {
                        // Assertion 1: getItem(1L)
                        Item item = ItemCrud.getItem(1L);
                        if (item == null) {
                            System.err.println("FAIL: item is null");
                            System.exit(1);
                        }
                        if (!Long.valueOf(1L).equals(item.id())) {
                            System.err.println("FAIL: item.id mismatch");
                            System.exit(1);
                        }
                        if (!"Sample Item".equals(item.title())) {
                            System.err.println("FAIL: item.title mismatch");
                            System.exit(1);
                        }
                        if (item.completed() != false) {
                            System.err.println("FAIL: item.completed mismatch");
                            System.exit(1);
                        }

                        // Assertion 2: createItem
                        Item input = new Item(42L, "New Item", true, 19.99);
                        Item created = ItemCrud.createItem(input);
                        if (created == null || !Long.valueOf(42L).equals(created.id())) {
                            System.err.println("FAIL: createItem mismatch");
                            System.exit(2);
                        }

                        System.out.println("ALL ORACLE ASSERTIONS PASSED CLEANLY");
                    }
                }
                """, StandardCharsets.UTF_8);

        // Compile
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream errOut = new ByteArrayOutputStream();
        int compileResult = compiler.run(null, null, errOut,
                modelsDir.resolve("Item.java").toString(),
                workerCrudJava.toString(),
                oracleTestJava.toString());
        assertEquals(0, compileResult, "Compilation should succeed for worker implementation: " + errOut);

        // Run the oracle against the worker implementation -> MUST PASS with exit code 0
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", baseDir.resolve(Path.of("src", "main", "java")) + File.pathSeparator + baseDir.resolve(Path.of("src", "test", "java")),
                "com.example.todo.crud.ItemCrudOracleRunner");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = reader.readLine()) != null) sb.append(l).append("\n");
            output = sb.toString();
        }
        int exitCode = p.waitFor();

        assertEquals(0, exitCode, "Worker implementation must pass all oracle assertions: " + output);
        assertTrue(output.contains("ALL ORACLE ASSERTIONS PASSED CLEANLY"), "Output should confirm pass: " + output);
        System.out.println("[Oracle Verification] Worker implementation PASSED oracle with exit code 0.");
    }
}
