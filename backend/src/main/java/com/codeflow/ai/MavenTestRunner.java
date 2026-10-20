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

@Service
public class MavenTestRunner {

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

        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.descendants()
                    .forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IllegalStateException("Maven tests timed out");
        }

        String output = outputFuture.join();

        if (process.exitValue() != 0) {
            throw new IllegalStateException(
                    "Maven tests failed:\n" + output);
        }

        return output;
    }
}
