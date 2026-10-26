package com.codeflow.task;

import org.springframework.stereotype.Service;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {

    private final TaskStore taskStore;

    public TaskService(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    public CodingTask createTask(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException(
                    "Task requirement must not be empty");
        }
        return taskStore.save(new CodingTask(requirement.trim()));
    }

    public CodingTask getTask(String id) {
        if (id == null || id.isBlank()
                || !id.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid task ID");
        }
        return taskStore.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    public List<CodingTask> getAllTasks() {
        return taskStore.findAll();
    }

    public CodingTask saveTask(CodingTask task) {
        return taskStore.save(task);
    }

    public CodingTask updatePlan(String id, String plan) {
        CodingTask task = getTask(id);
        task.setPlan(plan);
        return taskStore.save(task);
    }

    public CodingTask updateErrorMessage(
            String id, String errorMessage) {
        CodingTask task = getTask(id);
        task.setErrorMessage(errorMessage);
        return taskStore.save(task);
    }

    public CodingTask updateTaskStatus(
            String id, TaskStatus newStatus) {
        CodingTask task = getTask(id);

        // Jab agent status ko 'COMPLETED' karne lage tab validation trigger hoga
        if (newStatus == TaskStatus.COMPLETED) {
            File workspaceDir = new File("generated-workspace/tasks/" + id);

            List<String> missingRequirements = new ArrayList<>();

            // 1. Check if Controller file physically exists
            boolean hasController = containsFile(new File(workspaceDir, "src/main/java"), "Controller.java");

            // 2. Check if HTML templates exist in resources/templates
            boolean hasHtml = hasFiles(new File(workspaceDir, "src/main/resources/templates"));

            if (!hasController) {
                missingRequirements.add("Controller.java");
            }
            if (!hasHtml) {
                missingRequirements.add("HTML templates in src/main/resources/templates");
            }

            // Agar koi mandatory file missing hai toh status COMPLETED nahi hoga
            if (!missingRequirements.isEmpty()) {
                System.err.println("❌ Validation Failed for Task [" + id + "]. Missing: " + missingRequirements);

                // Status ko COMPLETED hone se roko aur errorMessage update kar do
                task.setErrorMessage("Incomplete project generation! Missing required files: " + String.join(", ", missingRequirements));
                return taskStore.save(task);
            }
        }

        // Target files disk par verify hone ke baad hi status change hoga
        task.updateStatus(newStatus);
        return taskStore.save(task);
    }

    // Helper: Checks if specific file type exists in subfolders
    private boolean containsFile(File dir, String suffix) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory() && containsFile(file, suffix)) return true;
            if (file.getName().endsWith(suffix)) return true;
        }
        return false;
    }

    // Helper: Checks if directory contains non-empty files
    private boolean hasFiles(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        return files != null && files.length > 0;
    }
}