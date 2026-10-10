import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.BashTool;
import tools.ReadFileTool;
import tools.WriteFileTool;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionTest {
    @TempDir
    Path tempDir;

    @Test
    void readAndWriteRoundTrip() throws Exception {
        Path file = tempDir.resolve("nested").resolve("note.txt");
        String written = new WriteFileTool().execute(
                "{\"file_path\":\"" + file.toString().replace("\\", "\\\\") + "\",\"content\":\"hello\"}");

        assertEquals("File written successfully", written);
        assertEquals("hello", Files.readString(file));

        String read = new ReadFileTool().execute(
                "{\"file_path\":\"" + file.toString().replace("\\", "\\\\") + "\"}");
        assertEquals("hello", read);
    }

    @Test
    void readRejectsADirectory() {
        String result = new ReadFileTool().execute(
                "{\"file_path\":\"" + tempDir.toString().replace("\\", "\\\\") + "\"}");

        assertTrue(result.startsWith("Error: "));
        assertTrue(result.contains("directory"));
    }

    @Test
    void bashReturnsCommandOutput() {
        String result = new BashTool().execute("{\"command\":\"echo hello-from-bash\"}");

        assertTrue(result.contains("hello-from-bash"), result);
    }
}
