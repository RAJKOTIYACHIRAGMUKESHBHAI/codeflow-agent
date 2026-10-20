
package com.codeflow;

import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodingTaskTest {

    @Test
    void shouldAllowValidStatusTransitions() {
        CodingTask task = new CodingTask("Add unit tests");

        task.updateStatus(TaskStatus.PLANNING);
        task.updateStatus(TaskStatus.WAITING_FOR_APPROVAL);
        task.updateStatus(TaskStatus.APPROVED);
        task.updateStatus(TaskStatus.APPLYING_CHANGES);
        task.updateStatus(TaskStatus.TESTING);
        task.updateStatus(TaskStatus.COMPLETED);

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        CodingTask task = new CodingTask("Add unit tests");

        assertThrows(
                IllegalStateException.class,
                () -> task.updateStatus(TaskStatus.COMPLETED)
        );

        assertEquals(TaskStatus.CREATED, task.getStatus());
    }

    @Test
    void shouldAllowFailureTransitionFromActiveStates() {
        CodingTask task1 = new CodingTask("Task 1");
        task1.updateStatus(TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, task1.getStatus());

        CodingTask task2 = new CodingTask("Task 2");
        task2.updateStatus(TaskStatus.PLANNING);
        task2.updateStatus(TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, task2.getStatus());

        CodingTask task3 = new CodingTask("Task 3");
        task3.updateStatus(TaskStatus.PLANNING);
        task3.updateStatus(TaskStatus.WAITING_FOR_APPROVAL);
        task3.updateStatus(TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, task3.getStatus());

        CodingTask task4 = new CodingTask("Task 4");
        task4.updateStatus(TaskStatus.PLANNING);
        task4.updateStatus(TaskStatus.WAITING_FOR_APPROVAL);
        task4.updateStatus(TaskStatus.APPROVED);
        task4.updateStatus(TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, task4.getStatus());
    }

    @Test
    void shouldRejectChangesAfterCompletion() {
        CodingTask task = new CodingTask("Add unit tests");

        task.updateStatus(TaskStatus.PLANNING);
        task.updateStatus(TaskStatus.WAITING_FOR_APPROVAL);
        task.updateStatus(TaskStatus.APPROVED);
        task.updateStatus(TaskStatus.APPLYING_CHANGES);
        task.updateStatus(TaskStatus.TESTING);
        task.updateStatus(TaskStatus.COMPLETED);

        assertThrows(
                IllegalStateException.class,
                () -> task.updateStatus(TaskStatus.FAILED)
        );
    }
}
