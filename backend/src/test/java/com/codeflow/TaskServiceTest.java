package com.codeflow;

import com.codeflow.task.CodingTask;
import com.codeflow.task.CodingTaskRepository;
import com.codeflow.task.TaskNotFoundException;
import com.codeflow.task.TaskService;
import com.codeflow.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TaskServiceTest {

    @Autowired
    private CodingTaskRepository repository;

    @Autowired
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateTask() {
        CodingTask task = taskService.createTask("  Add unit tests  ");

        assertNotNull(task.getId());
        assertEquals("Add unit tests", task.getRequirement());
        assertEquals(TaskStatus.CREATED, task.getStatus());
        assertEquals(1, repository.count());
    }

    @Test
    void shouldRejectEmptyRequirement() {
        assertThrows(IllegalArgumentException.class,
                () -> taskService.createTask("   "));
        assertEquals(0, repository.count());
    }

    @Test
    void shouldGetExistingTask() {
        CodingTask created = taskService.createTask("Test task retrieval");
        CodingTask found = taskService.getTask(created.getId());

        assertEquals(created.getId(), found.getId());
    }

    @Test
    void shouldThrowWhenTaskDoesNotExist() {
        assertThrows(TaskNotFoundException.class,
                () -> taskService.getTask("missing-id"));
    }

    @Test
    void shouldReturnAllTasks() {
        taskService.createTask("First task");
        taskService.createTask("Second task");

        assertEquals(2, taskService.getAllTasks().size());
    }
}
