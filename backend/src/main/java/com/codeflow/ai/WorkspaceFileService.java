package com.codeflow.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class WorkspaceFileService {

    private final Path workspace;

    public WorkspaceFileService(
            @Value("${codeflow.workspace:generated-workspace}")
            String workspacePath) throws IOException {

        this.workspace = Path.of(workspacePath)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(this.workspace);
    }

    public Path writeFile(String relativePath, String content)
            throws IOException {

        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException(
                    "File path must not be empty");
        }

        if (content == null) {
            throw new IllegalArgumentException(
                    "File content must not be null");
        }

        Path target = workspace.resolve(relativePath).normalize();

        if (!target.startsWith(workspace)
                || target.equals(workspace)) {
            throw new IllegalArgumentException(
                    "Invalid workspace path");
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