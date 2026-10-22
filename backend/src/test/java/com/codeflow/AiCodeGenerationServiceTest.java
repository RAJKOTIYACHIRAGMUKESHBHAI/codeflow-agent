package com.codeflow;

import com.codeflow.ai.AiCodeGenerationService;
import com.codeflow.ai.NebiusAiService;
import com.codeflow.task.CodingTask;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiCodeGenerationServiceTest {

    @Test
    void shouldStripMarkdownFences() {
        String input = "```java\npublic class Foo {}\n```";
        String cleaned = AiCodeGenerationService.cleanMarkdownFences(input);
        assertEquals("public class Foo {}", cleaned);
    }

    @Test
    void shouldHandleCleanCodeWithoutFences() {
        String input = "public class Foo {}";
        String cleaned = AiCodeGenerationService.cleanMarkdownFences(input);
        assertEquals("public class Foo {}", cleaned);
    }

    @Test
    void shouldStripMarkdownFencesWithSurroundingText() {
        String input = """
                Here is your complete test class:
                ```java
                package com.example;

                import org.junit.jupiter.api.Test;

                public class FooTest {
                    @Test
                    void test() {}
                }
                ```
                I hope this helps!
                """;
        String cleaned = AiCodeGenerationService.cleanMarkdownFences(input);
        assertTrue(cleaned.startsWith("package com.example;"));
        assertTrue(cleaned.endsWith("}"));
        assertFalse(cleaned.contains("```"));
    }

    @Test
    void shouldInjectRequiredJUnitImportsIntoTestSource() {
        String input = "package com.example;\n\npublic class CalculatorTest {\n    @Test\n    void addsNumbers() {\n        assertEquals(2, 1 + 1);\n    }\n}";

        String repaired = AiCodeGenerationService.ensureJUnitImports(input);

        assertTrue(repaired.contains("import org.junit.jupiter.api.Test;"));
        assertTrue(repaired.contains("import static org.junit.jupiter.api.Assertions.*;"));
        assertTrue(repaired.indexOf("package com.example;") < repaired.indexOf("import org.junit.jupiter.api.Test;"));
    }

    @Test
    void shouldPreserveExistingValidImportsAndPackage() {
        String input = """
                package com.example;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                import java.util.List;

                public class CalculatorTest {
                    @Test
                    void addsNumbers() {
                        assertEquals(2, 1 + 1);
                    }
                }
                """;

        String repaired = AiCodeGenerationService.ensureJUnitImports(input);

        assertTrue(repaired.startsWith("package com.example;"));
        assertTrue(repaired.contains("import java.util.List;"));
        assertTrue(repaired.contains("import org.junit.jupiter.api.Test;"));
        assertTrue(repaired.contains("import static org.junit.jupiter.api.Assertions.assertEquals;"));
        // Assert no duplicate import lines
        assertEquals(1, countOccurrences(repaired, "import org.junit.jupiter.api.Test;"));
    }

    @Test
    void shouldHoistImportsFromInsideClassBodyAndPassValidation() {
        // Confirmed defect from task e897b613-aa80-458a-b53b-5aa383ccc0fd:
        // The LLM placed imports at lines 17-18 inside the class body before the closing brace.
        String failingTaskOutput = """
                public class CalculatorTest {

                    @Test
                    void addTwoPositiveNumbersReturnsSum() {
                        Calculator calculator = new Calculator();
                        int result = calculator.add(2, 3);
                        assertEquals(5, result);
                    }

                    @Test
                    void addNegativeAndPositiveReturnsZero() {
                        Calculator calculator = new Calculator();
                        int result = calculator.add(-1, 1);
                        assertEquals(0, result);
                    }

                    import static org.junit.jupiter.api.Assertions.assertEquals;
                    import org.junit.jupiter.api.Test;

                }
                """;

        AiCodeGenerationService service = new AiCodeGenerationService(null);
        String validated = service.validateTestResponse(failingTaskOutput);

        // Imports must now appear before public class
        int importTestIdx = validated.indexOf("import org.junit.jupiter.api.Test;");
        int importAssertIdx = validated.indexOf("import static org.junit.jupiter.api.Assertions.");
        int classIdx = validated.indexOf("public class CalculatorTest");

        assertTrue(importTestIdx != -1 && importTestIdx < classIdx, "Test import must be before class");
        assertTrue(importAssertIdx != -1 && importAssertIdx < classIdx, "Assertions import must be before class");

        // The class body itself must not contain import statements
        int classOpenBrace = validated.indexOf('{', classIdx);
        String classBody = validated.substring(classOpenBrace);
        assertFalse(classBody.contains("import "), "Class body must not contain rogue import statements");

        // Both test methods must be present inside the class body
        assertTrue(classBody.contains("addTwoPositiveNumbersReturnsSum"));
        assertTrue(classBody.contains("addNegativeAndPositiveReturnsZero"));
    }

    @Test
    void shouldRejectMalformedSourceWithAnnotationOutsideClassBody() {
        String malformed = """
                @Test
                void addsNumbers() {
                    assertEquals(5, 2 + 3);
                }

                public class CalculatorTest {
                }
                """;

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AiCodeGenerationService.validateClassStructure(malformed)
        );
        assertTrue(ex.getMessage().contains("outside the class body"),
                "Expected message about content outside class body, but was: " + ex.getMessage());
    }

    @Test
    void shouldRejectMalformedSourceWithMethodsOutsideClassBody() {
        String malformed = """
                public class CalculatorTest {
                }

                @Test
                void addsNumbers() {
                    assertEquals(5, 2 + 3);
                }
                """;

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AiCodeGenerationService.validateClassStructure(malformed)
        );
        assertTrue(ex.getMessage().contains("outside the class body"),
                "Expected message about content outside class body, but was: " + ex.getMessage());
    }

    @Test
    void shouldRejectMalformedSourceWithUnbalancedBraces() {
        String unclosed = """
                public class CalculatorTest {
                    @Test
                    void addsNumbers() {
                        assertEquals(5, 2 + 3);
                """;

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AiCodeGenerationService.validateClassStructure(unclosed)
        );
        assertTrue(ex.getMessage().contains("unclosed class body or unbalanced braces"),
                "Expected message about unbalanced braces, but was: " + ex.getMessage());
    }

    @Test
    void shouldRetryAndRepairWhenFirstAttemptIsMalformed() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        NebiusAiService mockAi = new NebiusAiService("http://localhost", "dummy-key") {
            @Override
            public String generateResponse(String prompt) {
                if (callCount.incrementAndGet() == 1) {
                    // First call returns malformed code with method outside class
                    return """
                            public class CalculatorTest {
                            }
                            @Test
                            void addsNumbers() {
                                assertEquals(5, 2 + 3);
                            }
                            """;
                } else {
                    // Second call (repair) returns valid code
                    return """
                            import org.junit.jupiter.api.Test;
                            import static org.junit.jupiter.api.Assertions.*;

                            public class CalculatorTest {
                                @Test
                                void addsNumbers() {
                                    assertEquals(5, 2 + 3);
                                }
                            }
                            """;
                }
            }
        };

        AiCodeGenerationService service = new AiCodeGenerationService(mockAi);
        CodingTask task = new CodingTask("Add tests");
        task.setPlan("Plan for CalculatorTest");

        String result = service.generateTests(task, "public class Calculator {}");

        assertEquals(2, callCount.get(), "Service should have triggered repair call on malformed output");
        assertTrue(result.contains("public class CalculatorTest"));
        assertTrue(result.contains("@Test"));
    }

    @Test
    void shouldFailWhenRepairAlsoFails() {
        AtomicInteger callCount = new AtomicInteger(0);

        NebiusAiService mockAi = new NebiusAiService("http://localhost", "dummy-key") {
            @Override
            public String generateResponse(String prompt) {
                callCount.incrementAndGet();
                // Both attempts return unclosed / malformed code
                return "public class BrokenTest { @Test void foo() {";
            }
        };

        AiCodeGenerationService service = new AiCodeGenerationService(mockAi);
        CodingTask task = new CodingTask("Add tests");
        task.setPlan("Plan for CalculatorTest");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.generateTests(task, "public class Calculator {}")
        );

        assertEquals(2, callCount.get(), "Service should have tried initial generation and bounded repair");
        assertTrue(ex.getMessage().contains("unclosed class body or unbalanced braces"));
    }

    private static int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
