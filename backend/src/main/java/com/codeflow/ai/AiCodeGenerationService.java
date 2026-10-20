package com.codeflow.ai;

import com.codeflow.task.CodingTask;
import org.springframework.stereotype.Service;

@Service
public class AiCodeGenerationService {

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
                """.formatted(task.getRequirement(), task.getPlan());

        return validateCodeResponse(nebiusAiService.generateResponse(prompt));
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
                Do not generate the production implementation.
                Test the supplied implementation and task requirements.
                Use the same package as the implementation.
                Use the correct class name and method signatures.
               Generate only 5 to 7 concise test methods covering normal cases and important edge cases. Keep the entire test class short enough to fit in one response. Ensure every method, statement, and brace is complete. Do not stop before the final closing brace.
                Return source code only, without Markdown fences or explanations.
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

        return validateTestResponse(nebiusAiService.generateResponse(prompt));
    }

    private void validateTask(CodingTask task) {
        if (task == null || task.getPlan() == null
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
        if (!cleaned.contains("class ") && !cleaned.contains("interface ") && !cleaned.contains("record ") && !cleaned.contains("enum ")) {
            throw new IllegalStateException(
                    "AI returned unusable code: no Java type declaration found");
        }
        return cleaned;
    }

    private String validateTestResponse(String response) {
        String cleaned = cleanMarkdownFences(response);
        if (cleaned.isBlank()) {
            throw new IllegalStateException(
                    "AI returned empty test code");
        }
        if (!cleaned.contains("@Test") && !cleaned.contains("class ")) {
            throw new IllegalStateException(
                    "AI returned unusable tests: no JUnit test methods found");
        }
        return cleaned;
    }
}