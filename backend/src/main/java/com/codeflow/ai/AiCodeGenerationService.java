
package com.codeflow.ai;

import com.codeflow.task.CodingTask;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiCodeGenerationService {

    private static final Pattern MARKDOWN_CODE_FENCE_PATTERN =
            Pattern.compile("```(?:[a-zA-Z0-9_-]+)?\\s*\\R([\\s\\S]*?)\\R```");

    private static final Pattern PUBLIC_TYPE_PATTERN =
            Pattern.compile("(?m)\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed|strictfp)\\s+)*(?:class|record|enum|interface)\\s+([A-Za-z_$][\\w$]*)");

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+[^;\\r\\n]+;\\s*$");

    private static final Pattern IMPORT_LINE_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+[^;\\r\\n]+;\\s*$");

    private final NebiusAiService nebiusAiService;

    public AiCodeGenerationService(NebiusAiService nebiusAiService) {
        this.nebiusAiService = nebiusAiService;
    }

    public String generateCode(CodingTask task) throws Exception {
        validateTask(task);

        String prompt = """
                You are a Java source-code generator.
                Generate exactly ONE complete production Java class.
                Do not generate unit tests.
                Follow the approved plan and requirements.
                Use the package and class name specified by the plan.
                Include all imports before the class declaration.
                Return source code only, without Markdown fences or explanations.
                Never include a second class or package declaration.
                Do not claim that files were saved or tests were run.

                REQUIREMENTS:
                %s

                APPROVED PLAN:
                %s
                """.formatted(
                task.getRequirement(),
                task.getPlan());

        return validateCodeResponse(
                nebiusAiService.generateResponse(prompt));
    }

    public String generateTests(
            CodingTask task, String generatedCode) throws Exception {

        validateTask(task);

        if (generatedCode == null || generatedCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Generated implementation must not be empty");
        }

        String prompt = """
                You are a Java unit-test generator.
                Generate exactly ONE complete JUnit 5 test class.
                The test class MUST have a public class declaration,
                for example: public class GreetingTest.
                Never use a package-private test class.
                Include all required imports before the class declaration.
                Always include these imports:
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                Never omit imports used by annotations or assertions.
                Use the same package as the production implementation.
                Test the supplied implementation and task requirements.
                For a simple method, prefer one focused test that verifies
                the exact expected result. Do not add unnecessary edge cases.
                Include @Test and JUnit 5 assertions.
                Return only complete Java source code.
                Do not use Markdown fences or explanatory text.
                Do not include a second package declaration.
                Do not claim that tests were executed.

                REQUIREMENTS:
                %s

                APPROVED PLAN:
                %s

                PRODUCTION IMPLEMENTATION:
                %s
                """.formatted(
                task.getRequirement(),
                task.getPlan(),
                generatedCode);

        String response = nebiusAiService.generateResponse(prompt);

        try {
            return validateTestResponse(response);
        } catch (IllegalStateException firstFailure) {
            String repairPrompt = """
                    You are repairing invalid Java JUnit 5 test source.
                    Return exactly one complete Java test class.
                    It MUST contain a PUBLIC declaration such as
                    public class GreetingTest.
                    Include these exact imports before the class declaration:
                    import org.junit.jupiter.api.Test;
                    import static org.junit.jupiter.api.Assertions.*;
                    Never omit imports used by annotations or assertions.
                    Do not put import statements inside the class body.
                    Use the same package as the production implementation.
                    Test the actual implementation and stated requirement.
                    Ensure all methods and @Test annotations are inside the class body.
                    Return source code only, with no Markdown fences.
                    Do not return a package-private class.
                    Do not explain the repair.

                    REQUIREMENT:
                    %s

                    APPROVED PLAN:
                    %s

                    PRODUCTION IMPLEMENTATION:
                    %s

                    PREVIOUS INVALID RESPONSE:
                    %s
                    """.formatted(
                    task.getRequirement(),
                    task.getPlan(),
                    generatedCode,
                    response);

            try {
                return validateTestResponse(
                        nebiusAiService.generateResponse(repairPrompt));
            } catch (IllegalStateException secondFailure) {
                secondFailure.addSuppressed(firstFailure);
                throw secondFailure;
            }
        }
    }

    private void validateTask(CodingTask task) {
        if (task == null
                || task.getPlan() == null
                || task.getPlan().isBlank()) {
            throw new IllegalArgumentException(
                    "An approved implementation plan is required");
        }
    }

    public static String cleanMarkdownFences(String response) {
        if (response == null) {
            return "";
        }

        String trimmed = response.trim();

        Matcher fenceMatcher = MARKDOWN_CODE_FENCE_PATTERN.matcher(trimmed);
        if (fenceMatcher.find()) {
            return fenceMatcher.group(1).trim();
        }

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');

            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }

            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }

        return trimmed.trim();
    }

    private String validateCodeResponse(String response) {
        String cleaned = cleanMarkdownFences(response);

        if (cleaned.isBlank()) {
            throw new IllegalStateException(
                    "AI returned empty source code");
        }

        if (!cleaned.contains("class ")
                && !cleaned.contains("interface ")
                && !cleaned.contains("record ")
                && !cleaned.contains("enum ")) {
            throw new IllegalStateException(
                    "AI returned unusable code: no Java type declaration found");
        }

        return cleaned;
    }

    public static String ensureJUnitImports(String source) {
        String cleaned = cleanMarkdownFences(source);

        if (cleaned.isBlank()) {
            return cleaned;
        }

        Matcher typeMatcher = PUBLIC_TYPE_PATTERN.matcher(cleaned);
        if (!typeMatcher.find()) {
            return cleaned;
        }

        // Extract package declaration if any
        String packageDeclaration = "";
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(cleaned);
        if (packageMatcher.find()) {
            packageDeclaration = packageMatcher.group(0).trim();
        }

        // Collect all imports from anywhere in the file (preserving order)
        Set<String> imports = new LinkedHashSet<>();
        Matcher importMatcher = IMPORT_LINE_PATTERN.matcher(cleaned);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(0).trim());
        }

        // Remove all package and import lines from the source body
        String bodyWithoutPackageOrImports = PACKAGE_PATTERN.matcher(cleaned).replaceAll("");
        bodyWithoutPackageOrImports = IMPORT_LINE_PATTERN.matcher(bodyWithoutPackageOrImports).replaceAll("");

        // Find the start of the public type in the cleaned body
        Matcher bodyTypeMatcher = PUBLIC_TYPE_PATTERN.matcher(bodyWithoutPackageOrImports);
        if (!bodyTypeMatcher.find()) {
            return cleaned;
        }

        String classAndRemaining = bodyWithoutPackageOrImports.substring(bodyTypeMatcher.start()).trim();

        String requiredTestImport = "import org.junit.jupiter.api.Test;";
        String requiredAssertionsImport = "import static org.junit.jupiter.api.Assertions.*;";

        boolean hasTestImport = imports.contains(requiredTestImport);
        boolean hasAssertionsImport = imports.stream().anyMatch(imp ->
                imp.contains("org.junit.jupiter.api.Assertions"));

        if (!hasTestImport) {
            imports.add(requiredTestImport);
        }
        if (!hasAssertionsImport) {
            imports.add(requiredAssertionsImport);
        }

        StringBuilder repaired = new StringBuilder();
        if (!packageDeclaration.isEmpty()) {
            repaired.append(packageDeclaration).append(System.lineSeparator()).append(System.lineSeparator());
        }

        for (String imported : imports) {
            repaired.append(imported).append(System.lineSeparator());
        }

        if (!imports.isEmpty()) {
            repaired.append(System.lineSeparator());
        }

        repaired.append(classAndRemaining);
        return repaired.toString().trim() + System.lineSeparator();
    }

    public static void validateClassStructure(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("AI returned empty test code");
        }

        Matcher typeMatcher = PUBLIC_TYPE_PATTERN.matcher(source);
        if (!typeMatcher.find()) {
            throw new IllegalStateException(
                    "AI returned test code without a public Java type declaration. Regenerate the test class.");
        }

        int typeStart = typeMatcher.start();
        String beforeClass = source.substring(0, typeStart);
        if (beforeClass.contains("@Test")) {
            throw new IllegalStateException(
                    "Malformed test source: @Test annotation found outside the class body");
        }

        int openBrace = source.indexOf('{', typeMatcher.end());
        if (openBrace == -1) {
            throw new IllegalStateException("Malformed test source: missing opening brace for class body");
        }

        int depth = 0;
        int closeBrace = -1;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = (i + 1 < source.length()) ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    closeBrace = i;
                    break;
                }
            }
        }

        if (depth != 0 || closeBrace == -1) {
            throw new IllegalStateException(
                    "Malformed test source: unclosed class body or unbalanced braces");
        }

        String afterClass = source.substring(closeBrace + 1);
        String afterClassNoComments = afterClass.replaceAll("//.*", "").replaceAll("/\\*[\\s\\S]*?\\*/", "").trim();
        if (!afterClassNoComments.isEmpty() && !afterClassNoComments.equals(";")) {
            throw new IllegalStateException(
                    "Malformed test source: methods or statements found outside the class body");
        }

        String classBody = source.substring(openBrace, closeBrace + 1);
        if (!classBody.contains("@Test")) {
            throw new IllegalStateException(
                    "AI returned unusable tests: no JUnit @Test method found inside class body");
        }
    }

    public String validateTestResponse(String response) {
        String cleaned = cleanMarkdownFences(response);

        if (cleaned.isBlank()) {
            throw new IllegalStateException(
                    "AI returned empty test code");
        }

        cleaned = ensureJUnitImports(cleaned);

        validateClassStructure(cleaned);

        if (!cleaned.contains("import org.junit.jupiter.api.Test;")) {
            throw new IllegalStateException(
                    "JUnit 5 Test import is missing");
        }

        boolean hasStaticAssertionsImport =
                cleaned.contains(
                        "import static org.junit.jupiter.api.Assertions.");

        boolean hasAssertionsClassImport =
                cleaned.contains(
                        "import org.junit.jupiter.api.Assertions;");

        if (!hasStaticAssertionsImport && !hasAssertionsClassImport) {
            throw new IllegalStateException(
                    "JUnit 5 Assertions import is missing");
        }

        return cleaned;
    }
}
