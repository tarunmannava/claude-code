import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Env {
    private static final Map<String, String> DOTENV = new HashMap<>();

    static {
        loadDotenv(Path.of(".env"));
    }

    public static void loadDotenv(Path envFile) {
        if (Files.exists(envFile)) {
            try {
                List<String> lines = Files.readAllLines(envFile);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String val = line.substring(eq + 1).trim();
                        if ((val.startsWith("\"") && val.endsWith("\"")) ||
                            (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        DOTENV.put(key, val);
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    public static String get(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        val = DOTENV.get(key);
        return (val != null && !val.isBlank()) ? val : null;
    }

    public static String get(String key, String defaultValue) {
        String val = get(key);
        return val != null ? val : defaultValue;
    }
}
