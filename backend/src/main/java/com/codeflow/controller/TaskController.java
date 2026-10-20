
package com.codeflow.controller;

import com.codeflow.ai.AiPlanningService;
import com.codeflow.ai.CodeExecutionService;
import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final AiPlanningService planningService;
    private final CodeExecutionService executionService;

    public TaskController(
            TaskService taskService,
            AiPlanningService planningService,
            CodeExecutionService executionService) {
        this.taskService = taskService;
        this.planningService = planningService;
        this.executionService = executionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CodingTask createTask(
            @Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(request.requirement());
    }

    @PostMapping("/{id}/plan")
    public Map<String, String> createPlan(@PathVariable String id)
            throws Exception {
        return Map.of("taskId", id, "plan",
                planningService.createPlan(id));
    }

    @PostMapping("/{id}/approve")
    public CodingTask approvePlan(@PathVariable String id) {
        return planningService.approvePlan(id);
    }

    @PostMapping("/{id}/execute")
    public Map<String, String> executeTask(@PathVariable String id)
            throws Exception {
        return executionService.executeApprovedTask(id);
    }

    @GetMapping("/{id}")
    public CodingTask getTask(@PathVariable String id) {
        return taskService.getTask(id);
    }

    @GetMapping
    public List<CodingTask> getAllTasks() {
        return taskService.getAllTasks();
    }

    public record CreateTaskRequest(
            @NotBlank(message = "Requirement must not be empty")
            @Size(max = 5000,
                    message = "Requirement must not exceed 5000 characters")
            String requirement
    ) {}
}
