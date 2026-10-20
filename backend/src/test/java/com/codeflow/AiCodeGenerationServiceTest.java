package com.codeflow;

import com.codeflow.ai.AiCodeGenerationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
