package com.codeflow;

import com.codeflow.ai.AiCodeGenerationService;
import com.codeflow.ai.CodeExecutionService;
import com.codeflow.ai.WorkspaceFileService;
import com.codeflow.task.CodingTask;
import com.codeflow.task.CodingTaskRepository;
import com.codeflow.task.TaskService;
import com.codeflow.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CodeExecutionServiceTest {

    @Autowired
    private CodingTaskRepository repository;

    @Autowired
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldRejectUnapprovedTask() {
        CodingTask task = taskService.createTask("Test requirement");
        CodeExecutionService service =
                new CodeExecutionService(taskService, null, null, null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.executeApprovedTask(task.getId()));

        assertEquals("Task must be approved before execution",
                ex.getMessage());
    }

    @Test
    void shouldMarkTaskFailedAndRecordErrorWhenGenerationFails()
            throws Exception {
        CodingTask task = taskService.createTask("Test requirement");

        taskService.updateTaskStatus(task.getId(), TaskStatus.PLANNING);
        taskService.updatePlan(task.getId(), "Test plan");
        taskService.updateTaskStatus(
                task.getId(), TaskStatus.WAITING_FOR_APPROVAL);
        taskService.updateTaskStatus(task.getId(), TaskStatus.APPROVED);

        WorkspaceFileService workspaceFileService =
                new WorkspaceFileService("generated-workspace");

        AiCodeGenerationService failingGenService =
                new AiCodeGenerationService(null) {
                    @Override
                    public String generateCode(CodingTask t)
                            throws Exception {
                        throw new RuntimeException(
                                "AI generation service unavailable");
                    }
                };

        CodeExecutionService service = new CodeExecutionService(
                taskService, failingGenService, workspaceFileService, null);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> service.executeApprovedTask(task.getId()));

        assertEquals("AI generation service unavailable", ex.getMessage());

        CodingTask saved = taskService.getTask(task.getId());
        assertEquals(TaskStatus.FAILED, saved.getStatus());
        assertEquals("AI generation service unavailable",
                saved.getErrorMessage());
    }
}
