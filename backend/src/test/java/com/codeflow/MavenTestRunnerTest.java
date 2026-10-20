package com.codeflow;

import com.codeflow.ai.MavenTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MavenTestRunnerTest {

    @TempDir
    Path tempDir;

    private MavenTestRunner createRunner(int exitCode) throws Exception {
        Path wrapper = tempDir.resolve("fake-mvnw.cmd");

        Files.writeString(
                wrapper,
                "@echo off\r\n" +
                "echo Fake Maven output\r\n" +
                "exit /b " + exitCode + "\r\n",
                StandardCharsets.UTF_8
        );

        return new MavenTestRunner(wrapper.toString());
    }

    private Path createProject() throws Exception {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        return project;
    }

    @Test
    void shouldReturnOutputWhenMavenSucceeds() throws Exception {
        MavenTestRunner runner = createRunner(0);

        String output = runner.runTests(createProject());

        assertTrue(output.contains("Fake Maven output"));
    }

    @Test
    void shouldThrowExceptionWhenMavenFails() throws Exception {
        MavenTestRunner runner = createRunner(1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> runner.runTests(createProject())
        );

        assertTrue(exception.getMessage().contains("Maven tests failed"));
        assertTrue(exception.getMessage().contains("Fake Maven output"));
    }

    @Test
    void shouldRejectProjectWhenPomIsMissing() throws Exception {
        createRunner(0);

        Path wrapper = tempDir.resolve("fake-mvnw.cmd");
        MavenTestRunner runner = new MavenTestRunner(wrapper.toString());
        Path project = tempDir.resolve("missing-pom-project");
        Files.createDirectories(project);

        assertThrows(
                IllegalArgumentException.class,
                () -> runner.runTests(project)
        );
    }

    @Test
    void shouldRejectWhenMavenWrapperIsMissing() {
        Path missingWrapper = tempDir.resolve("does-not-exist.cmd");
        MavenTestRunner runner = new MavenTestRunner(missingWrapper.toString());

        Path project = tempDir.resolve("valid-project");
        assertDoesNotThrow(() -> {
            Files.createDirectories(project);
            Files.writeString(project.resolve("pom.xml"), "<project/>");
        });

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> runner.runTests(project)
        );

        assertTrue(exception.getMessage().contains("Maven wrapper not found"));
    }
}