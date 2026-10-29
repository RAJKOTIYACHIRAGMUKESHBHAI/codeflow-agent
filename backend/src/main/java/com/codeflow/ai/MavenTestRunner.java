package com.codeflow.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class MavenTestRunner {

    private static final long TEST_TIMEOUT_SECONDS = 120;

    private final Path mavenWrapper;
    private final boolean windows;

    public MavenTestRunner(
            @Value("${codeflow.maven-wrapper:}") String wrapperPath) {

        this.windows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win");

        String wrapperName = windows ? "mvnw.cmd" : "mvnw";

        if (wrapperPath != null && !wrapperPath.isBlank()) {
            this.mavenWrapper = Path.of(wrapperPath)
                    .toAbsolutePath().normalize();
        } else {
            Path candidate = Path.of(wrapperName)
                    .toAbsolutePath().normalize();

            if (Files.isRegularFile(candidate)) {
                this.mavenWrapper = candidate;
            } else {
                candidate = Path.of("backend", wrapperName)
                        .toAbsolutePath().normalize();

                this.mavenWrapper = candidate;
            }
        }
    }

    public String runTests(Path projectDirectory)
            throws IOException, InterruptedException {

        Path project = projectDirectory.toAbsolutePath().normalize();
        Path pom = project.resolve("pom.xml");

        if (!Files.isRegularFile(mavenWrapper)) {
            throw new IllegalStateException(
                    "Maven wrapper not found: " + mavenWrapper);
        }

        if (!Files.isRegularFile(pom)) {
            throw new IllegalArgumentException(
                    "A valid Maven project with pom.xml is required");
        }

        List<String> command = new ArrayList<>();

        if (windows) {
            command.add("cmd.exe");
            command.add("/c");
        }

        command.add(mavenWrapper.toString());
        command.add("-B");
        command.add("-ntp");
        command.add("-f");
        command.add(pom.toString());
        command.add("test");

        Process process = new ProcessBuilder(command)
                .directory(project.toFile())
                .redirectErrorStream(true)
                .start();

        CompletableFuture<String> outputFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return new String(
                                process.getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                });

        boolean completed;

        try {
            completed = process.waitFor(
                    TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            terminateProcess(process);
            Thread.currentThread().interrupt();
            throw e;
        }

        if (!completed) {
            terminateProcess(process);

            String output = collectOutput(outputFuture);

            throw new IllegalStateException(
                    "Maven tests timed out after "
                            + TEST_TIMEOUT_SECONDS
                            + " seconds.\n"
                            + "Maven output before timeout:\n"
                            + output);
        }

        String output = collectOutput(outputFuture);

        if (process.exitValue() != 0) {
            throw new IllegalStateException(
                    "Maven tests failed with exit code "
                            + process.exitValue()
                            + ":\n"
                            + output);
        }

        return output;
    }

    private void terminateProcess(Process process) {
        process.descendants()
                .forEach(ProcessHandle::destroyForcibly);

        process.destroyForcibly();

        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String collectOutput(
            CompletableFuture<String> outputFuture) {

        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "[Maven output could not be fully collected.]";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[Interrupted while collecting Maven output.]";
        } catch (java.util.concurrent.ExecutionException e) {
            return "[Could not read Maven output: "
                    + e.getCause() + "]";
        }
    }
}
