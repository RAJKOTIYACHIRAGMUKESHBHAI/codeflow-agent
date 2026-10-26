package com.codeflow.controller;

import com.codeflow.ai.AiPlanningService;
import com.codeflow.ai.NebiusAiService;
import com.codeflow.ai.CodeExecutionService;
import com.codeflow.ai.WorkspaceFileService;
import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskNotFoundException;
import com.codeflow.task.TaskService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final NebiusAiService nebiusAiService;
    private final AiPlanningService aiPlanningService;
    private final CodeExecutionService codeExecutionService;
    private final WorkspaceFileService workspaceFileService;
    private final TaskService taskService;

    public AiController(
            NebiusAiService nebiusAiService,
            AiPlanningService aiPlanningService,
            CodeExecutionService codeExecutionService,
            WorkspaceFileService workspaceFileService,
            TaskService taskService) {
        this.nebiusAiService = nebiusAiService;
        this.aiPlanningService = aiPlanningService;
        this.codeExecutionService = codeExecutionService;
        this.workspaceFileService = workspaceFileService;
        this.taskService = taskService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> request) {

        String prompt = request.get("prompt");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Prompt must not be empty"));
        }

        try {
            String answer =
                    nebiusAiService.generateResponse(prompt);

            return ResponseEntity.ok(
                    Map.of("response", answer));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error", "AI service request failed"));
        }
    }

    @PostMapping("/plan/{taskId}")
    public ResponseEntity<Map<String, String>> createPlan(
            @PathVariable String taskId) {

        try {
            String plan = aiPlanningService.createPlan(taskId);

            return ResponseEntity.ok(
                    Map.of("plan", plan));

        } catch (TaskNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Task not found"));

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "AI planning failed: " + ex.getMessage()));
        }
    }

    @PostMapping("/approve/{taskId}")
    public ResponseEntity<Map<String, String>> approvePlan(
            @PathVariable String taskId) {

        try {
            CodingTask task = aiPlanningService.approvePlan(taskId);

            // Plan approve hote hi AI Plan ki saari files auto-extract karke write hongi
            if (task.getPlan() != null && !task.getPlan().isBlank()) {
                workspaceFileService.extractAndWriteAllPlanFiles(taskId, task.getPlan());
            }

            return ResponseEntity.ok(Map.of(
                    "taskId", task.getId(),
                    "status", task.getStatus().name(),
                    "task", task.toString()
            ));

        } catch (TaskNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Task not found"));

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "AI approval failed: " + ex.getMessage()));
        }
    }

    @PostMapping("/execute/{taskId}")
    public ResponseEntity<Map<String, String>> executeApprovedTask(
            @PathVariable String taskId) {

        try {
            // Maven tests chalne se PEHLE AI Plan ki saari files (`DTO`, `Controller`, `HTML`, `Test`) disk par write hungi
            CodingTask task = taskService.getTask(taskId);
            if (task.getPlan() != null && !task.getPlan().isBlank()) {
                workspaceFileService.extractAndWriteAllPlanFiles(taskId, task.getPlan());
            }

            Map<String, String> result = codeExecutionService.executeApprovedTask(taskId);

            return ResponseEntity.ok(result);

        } catch (TaskNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Task not found"));

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Code execution failed: " + ex.getMessage()));
        }
    }
}