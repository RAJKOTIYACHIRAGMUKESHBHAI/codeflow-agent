package com.codeflow.task;

import org.springframework.stereotype.Service;
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
        task.updateStatus(newStatus);
        return taskStore.save(task);
    }
}
