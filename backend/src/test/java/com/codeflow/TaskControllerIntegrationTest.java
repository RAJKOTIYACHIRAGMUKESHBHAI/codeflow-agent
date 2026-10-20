package com.codeflow;

import com.codeflow.task.CodingTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CodingTaskRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateTaskThroughHttp() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"Add integration tests"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.requirement")
                        .value("Add integration tests"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void shouldReturnAllTasks() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void shouldReturnNotFoundForUnknownTask() throws Exception {
        mockMvc.perform(get("/api/tasks/missing-task-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidTaskId() throws Exception {
        mockMvc.perform(get("/api/tasks/invalid@id!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectBlankRequirement() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
