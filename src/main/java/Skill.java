import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Skill {
    public final String name;
    public final String description;
    public final String body;
    public final Path dir;

    public Skill(String name, String description, String body, Path dir) {
        this.name = name;
        this.description = description;
        this.body = body;
        this.dir = dir;
    }

    public static Skill load(Path skillFile, Path dir) {
        try {
            List<String> lines = Files.readAllLines(skillFile, StandardCharsets.UTF_8);
            int first = -1;
            int second = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).replace("\uFEFF", "").trim();
                if (line.equals("---")) {
                    if (first == -1) {
                        first = i;
                    } else {
                        second = i;
                        break;
                    }
                }
            }

            String yamlContent = "";
            String body = "";
            if (first != -1 && second != -1 && second > first) {
                yamlContent = String.join("\n", lines.subList(first + 1, second));
                body = String.join("\n", lines.subList(second + 1, lines.size())).strip();
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(yamlContent);

            String name = null;
            String description = "";
            if (data != null) {
                Object nameObj = data.get("name");
                if (nameObj != null) {
                    name = nameObj.toString().trim();
                }
                Object descObj = data.get("description");
                if (descObj != null) {
                    description = descObj.toString().trim();
                }
            }

            if (name == null || name.isEmpty()) {
                name = dir.getFileName().toString();
            }

            return new Skill(name, description, body, dir);
        } catch (Exception e) {
            System.err.println("Failed to load skill from " + skillFile + ": " + e.getMessage());
            return null;
        }
    }

    public static List<Skill> loadAll() {
        return loadAll(Path.of(".claude", "skills"));
    }

    public static List<Skill> loadAll(Path skillsDir) {
        List<Skill> skills = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return skills;
        }

        try (Stream<Path> stream = Files.list(skillsDir)) {
            List<Path> subDirs = stream.filter(Files::isDirectory).sorted().toList();
            for (Path dir : subDirs) {
                Path skillFile = dir.resolve("SKILL.md");
                if (!Files.isRegularFile(skillFile)) {
                    try (Stream<Path> dirFiles = Files.list(dir)) {
                        skillFile = dirFiles
                                .filter(f -> f.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                                .findFirst()
                                .orElse(null);
                    }
                }
                if (skillFile != null && Files.isRegularFile(skillFile)) {
                    Skill skill = load(skillFile, dir);
                    if (skill != null) {
                        skills.add(skill);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading skills directory: " + e.getMessage());
        }

        skills.sort(Comparator.comparing(s -> s.name));
        return skills;
    }

    public static String formatSkillsPrompt(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("You have access to the following skills:\n\n");
        for (int i = 0; i < skills.size(); i++) {
            Skill s = skills.get(i);
            sb.append("- ").append(s.name).append(": ").append(s.description);
            if (i < skills.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
