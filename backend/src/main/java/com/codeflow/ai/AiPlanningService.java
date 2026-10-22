
package com.codeflow.ai;

import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskService;
import com.codeflow.task.TaskStatus;
import org.springframework.stereotype.Service;

@Service
public class AiPlanningService {

    private final NebiusAiService nebiusAiService;
    private final TaskService taskService;

    public AiPlanningService(
            NebiusAiService nebiusAiService,
            TaskService taskService) {
        this.nebiusAiService = nebiusAiService;
        this.taskService = taskService;
    }

    public synchronized String createPlan(String taskId) throws Exception {
        CodingTask task = taskService.getTask(taskId);

        // A repeated request should reuse an already generated plan.
        if (task.getStatus() == TaskStatus.WAITING_FOR_APPROVAL
                && task.getPlan() != null
                && !task.getPlan().isBlank()) {
            return task.getPlan();
        }

        // Do not silently restart failed or in-progress tasks.
        if (task.getStatus() != TaskStatus.CREATED) {
            String detail = task.getErrorMessage() == null
                    || task.getErrorMessage().isBlank()
                    ? ""
                    : ". Previous error: " + task.getErrorMessage();

            throw new IllegalStateException(
                    "Cannot generate a plan for task in status "
                            + task.getStatus() + detail);
        }

        taskService.updateTaskStatus(taskId, TaskStatus.PLANNING);

        String prompt = """
            You are a senior Java 17 software engineer.
            This project uses Java and Maven.
            Plan only Java 17 solutions with JUnit 5 tests.
            Never suggest Node.js, JavaScript, npm, Express,
            or .js files for this project.
            Prefer the smallest implementation that satisfies
            the user's requirement.
            Include:
            1. Required changes
            2. Java production and test files
            3. Testing strategy
            Do not claim that files have been modified.
            Return the plan only.

            Task requirement:
            """ + task.getRequirement();

        try {
            String plan = nebiusAiService.generateResponse(prompt);

            if (plan == null || plan.isBlank()) {
                throw new IllegalStateException(
                        "AI returned an empty implementation plan");
            }

            taskService.updatePlan(taskId, plan);
            taskService.updateTaskStatus(
                    taskId, TaskStatus.WAITING_FOR_APPROVAL);

            return plan;
        } catch (Exception ex) {
            taskService.updateErrorMessage(
                    taskId,
                    ex.getMessage() == null
                            ? ex.getClass().getSimpleName()
                            : ex.getMessage());

            CodingTask current = taskService.getTask(taskId);
            if (current.getStatus() == TaskStatus.PLANNING) {
                taskService.updateTaskStatus(taskId, TaskStatus.FAILED);
            }

            throw ex;
        }
    }

    public CodingTask approvePlan(String taskId) {
        CodingTask task = taskService.getTask(taskId);

        if (task.getPlan() == null || task.getPlan().isBlank()) {
            throw new IllegalStateException("Task does not have an implementation plan to approve");
        }

        taskService.updateTaskStatus(
                taskId, TaskStatus.APPROVED
        );

        return task;
    }
}
