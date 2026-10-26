package com.codeflow.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class WorkspaceFileService {

    private static final Pattern SAFE_TASK_ID = Pattern.compile("[a-zA-Z0-9_-]+");
    private final Path workspace;

    public WorkspaceFileService(
            @Value("${codeflow.workspace:generated-workspace}")
            String workspacePath) throws IOException {

        this.workspace = Path.of(workspacePath)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(this.workspace);
    }

    public Path getTaskDirectory(String taskId) {
        if (taskId == null || !SAFE_TASK_ID.matcher(taskId).matches()) {
            throw new IllegalArgumentException("Invalid task ID for workspace");
        }

        Path taskDirectory = workspace.resolve("tasks")
                .resolve(taskId).normalize();

        if (!taskDirectory.startsWith(workspace) || taskDirectory.equals(workspace)) {
            throw new IllegalArgumentException("Invalid workspace path");
        }

        return taskDirectory;
    }

    /**
     * Clears only this task's generated directory so a failed task can be retried.
     */
    public void resetTaskWorkspace(String taskId) throws IOException {
        Path taskDirectory = getTaskDirectory(taskId);
        if (Files.exists(taskDirectory)) {
            List<Path> paths;
            try (Stream<Path> stream = Files.walk(taskDirectory)) {
                paths = stream.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
        Files.createDirectories(taskDirectory);
    }

    /**
     * Writes or overwrites a file relative to workspace root.
     */
    public Path writeFile(String relativePath, String content) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("File content must not be null");
        }

        Path target = workspace.resolve(relativePath).normalize();

        if (!target.startsWith(workspace) || target.equals(workspace)) {
            throw new IllegalArgumentException("Invalid workspace path");
        }

        Files.createDirectories(target.getParent());

        // Overwrite mode enabled: CREATE + TRUNCATE_EXISTING + WRITE
        return Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Writes or overwrites a file directly inside a specific task workspace directory.
     */
    public Path writeTaskFile(String taskId, String relativePath, String content) throws IOException {
        Path taskDir = getTaskDirectory(taskId);
        Path target = taskDir.resolve(relativePath).normalize();

        if (!target.startsWith(taskDir)) {
            throw new IllegalArgumentException("Invalid task workspace path");
        }

        Files.createDirectories(target.getParent());

        return Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Parses the AI Plan markdown, extracts ALL ```java and ```html code blocks,
     * determines package/file paths, and writes EVERY file to disk.
     */
    public List<String> extractAndWriteAllPlanFiles(String taskId, String aiPlan) throws IOException {
        List<String> writtenFiles = new ArrayList<>();
        if (aiPlan == null || aiPlan.isBlank()) {
            return writtenFiles;
        }

        // Match all ```java and ```html code blocks in the plan
        Pattern codeBlockPattern = Pattern.compile("```(java|html)\\s*\\n([\\s\\S]*?)\\n```", Pattern.CASE_INSENSITIVE);
        Matcher matcher = codeBlockPattern.matcher(aiPlan);

        while (matcher.find()) {
            String lang = matcher.group(1).toLowerCase();
            String code = matcher.group(2).trim();

            if (code.isEmpty()) continue;

            String relativePath = null;

            if ("java".equals(lang)) {
                Matcher pkgMatcher = Pattern.compile("package\\s+([\\w\\.]+);").matcher(code);
                Matcher classMatcher = Pattern.compile("public\\s+(?:class|interface|enum|record)\\s+(\\w+)").matcher(code);

                if (pkgMatcher.find() && classMatcher.find()) {
                    String pkg = pkgMatcher.group(1).replace('.', '/');
                    String className = classMatcher.group(1);

                    boolean isTest = className.endsWith("Test") || className.endsWith("Tests") || code.contains("org.junit");
                    String baseFolder = isTest ? "src/test/java/" : "src/main/java/";
                    relativePath = baseFolder + pkg + "/" + className + ".java";
                }
            } else if ("html".equals(lang)) {
                // Extract filename from text preceding the block, or fallback
                Matcher htmlNameMatcher = Pattern.compile("([\\w-]+\\.html)").matcher(aiPlan.substring(0, matcher.start()));
                String htmlName = "course.html";
                while (htmlNameMatcher.find()) {
                    htmlName = htmlNameMatcher.group(1);
                }
                relativePath = "src/main/resources/templates/" + htmlName;
            }

            if (relativePath != null) {
                writeTaskFile(taskId, relativePath, code);
                writtenFiles.add(relativePath);
                System.out.println("✅ Extracted & Saved to Disk: " + relativePath);
            }
        }

        return writtenFiles;
    }
}