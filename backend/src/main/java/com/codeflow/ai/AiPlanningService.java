
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

    public String createPlan(String taskId) throws Exception {
        CodingTask task = taskService.getTask(taskId);

        taskService.updateTaskStatus(
                taskId, TaskStatus.PLANNING);

        String prompt = """
                You are a software engineering planning assistant.
                Create a concise implementation plan for this task.
                Include:
                1. Required changes
                2. Files likely to be affected
                3. Testing strategy
                Do not claim that any files have been modified.
                Return the plan only.

                Task requirement:
                """ + task.getRequirement();

        try {
            String plan = nebiusAiService.generateResponse(prompt);
            taskService.updatePlan(taskId, plan);

            taskService.updateTaskStatus(
                    taskId, TaskStatus.WAITING_FOR_APPROVAL);

            return plan;
        } catch (Exception ex) {
            taskService.updateErrorMessage(taskId, ex.getMessage());
            taskService.updateTaskStatus(taskId, TaskStatus.FAILED);
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
