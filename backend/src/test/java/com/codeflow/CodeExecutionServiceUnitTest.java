package com.codeflow;

import com.codeflow.ai.AiCodeGenerationService;
import com.codeflow.ai.CodeExecutionService;
import com.codeflow.ai.MavenTestRunner;
import com.codeflow.ai.WorkspaceFileService;
import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskService;
import com.codeflow.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CodeExecutionServiceUnitTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldMarkTaskFailedAndRecordErrorWhenMavenCompilationFails() throws Exception {
        TaskService taskService = mock(TaskService.class);
        String taskId = "test-task-123";

        CodingTask task = new CodingTask("Create Calculator");
        task.updateStatus(TaskStatus.PLANNING);
        task.updateStatus(TaskStatus.WAITING_FOR_APPROVAL);
        task.updateStatus(TaskStatus.APPROVED);

        when(taskService.getTask(taskId)).thenReturn(task);

        WorkspaceFileService workspaceFileService =
                new WorkspaceFileService(tempDir.toString());

        AiCodeGenerationService codeGenService = new AiCodeGenerationService(null) {
            @Override
            public String generateCode(CodingTask t) {
                return "public class Calculator { public int add(int a, int b) { return a + b; } }";
            }

            @Override
            public String generateTests(CodingTask t, String code) {
                return """
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class CalculatorTest {
                            @Test
                            void testAdd() {
                                assertEquals(5, 2 + 3);
                            }
                        }
                        """;
            }
        };

        MavenTestRunner mavenTestRunner = mock(MavenTestRunner.class);
        String mavenErrorMessage = "Maven tests failed:\n[ERROR] CalculatorTest.java:[17,5] illegal start of type";
        when(mavenTestRunner.runTests(any(Path.class)))
                .thenThrow(new IllegalStateException(mavenErrorMessage));

        CodeExecutionService service = new CodeExecutionService(
                taskService,
                codeGenService,
                workspaceFileService,
                mavenTestRunner
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.executeApprovedTask(taskId)
        );

        assertTrue(ex.getMessage().contains("Maven tests failed"));

        // Verify task state transitions and error recording
        verify(taskService).updateErrorMessage(taskId, mavenErrorMessage);
        verify(taskService).updateTaskStatus(taskId, TaskStatus.FAILED);
    }
}
