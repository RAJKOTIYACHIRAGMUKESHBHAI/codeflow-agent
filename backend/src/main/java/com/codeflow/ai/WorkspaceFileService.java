package com.codeflow.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
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

    public Path writeFile(String relativePath, String content)
            throws IOException {

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

        if (Files.exists(target)) {
            throw new IllegalStateException(
                    "File already exists; refusing to overwrite");
        }

        Files.createDirectories(target.getParent());

        return Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }
}
