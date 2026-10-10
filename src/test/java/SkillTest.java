import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTest {
    private final Skill inspect = new Skill(
            "inspect",
            "Inspect a codebase",
            "Look at $ARGUMENTS and then $0",
            Path.of("skills", "inspect"));
    private final Skill migrate = new Skill(
            "migrate",
            "Migrate a project",
            "Translate $0 into $1. Notes: $ARGUMENTS",
            Path.of("skills", "migrate"),
            "fork");

    @Test
    void plainTextIsNotASkillRun() {
        Skill.ParsedPrompt parsed = Skill.parsePrompt("read the file", List.of(inspect, migrate));

        assertTrue(parsed.skills().isEmpty());
        assertEquals("read the file", parsed.argumentsText());
    }

    @Test
    void stackedSkillsShareArgumentsUntilAFork() {
        Skill.ParsedPrompt parsed = Skill.parsePrompt(
                "/inspect /migrate src target",
                List.of(inspect, migrate));

        assertEquals(List.of(inspect, migrate), parsed.skills());
        assertEquals("src target", parsed.argumentsText());
    }

    @Test
    void forkStopsTheStackAndKeepsTheRemainderAsArguments() {
        Skill.ParsedPrompt parsed = Skill.parsePrompt(
                "/migrate /inspect src",
                List.of(inspect, migrate));

        assertEquals(List.of(migrate), parsed.skills());
        assertEquals("/inspect src", parsed.argumentsText());
    }

    @Test
    void unmatchedTokenEndsTheRun() {
        Skill.ParsedPrompt parsed = Skill.parsePrompt("/missing file.txt", List.of(inspect));

        assertTrue(parsed.skills().isEmpty());
        assertEquals("/missing file.txt", parsed.argumentsText());
    }

    @Test
    void argumentPlaceholdersAreSubstitutedOnce() {
        assertEquals(
                "Look at hello $1 and then hello",
                inspect.applyArguments("hello $1"));
        assertEquals(
                "Translate src into target. Notes: src target",
                migrate.applyArguments("src target"));
        assertEquals("Translate  into . Notes: ", migrate.applyArguments(""));
    }

    @Test
    void renderDisclosesTheSkillDirectory() {
        assertEquals(
                "Skill: inspect (located at skills/inspect)\n"
                        + "Paths in the instructions below are relative to that folder.\n\n"
                        + "Look at src and then src",
                inspect.render("src"));
    }
}
