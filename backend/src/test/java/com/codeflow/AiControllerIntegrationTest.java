package com.codeflow;

import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    @Test
    void shouldRejectEmptyChatPrompt() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Prompt must not be empty"));
    }

    @Test
    void shouldReturnNotFoundForUnknownTaskInPlan() throws Exception {
        mockMvc.perform(post("/api/ai/plan/unknown-task-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found"));
    }

    @Test
    void shouldReturnNotFoundForUnknownTaskInApprove() throws Exception {
        mockMvc.perform(post("/api/ai/approve/unknown-task-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found"));
    }

    @Test
    void shouldReturnNotFoundForUnknownTaskInExecute() throws Exception {
        mockMvc.perform(post("/api/ai/execute/unknown-task-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found"));
    }

    @Test
    void shouldRejectApprovingTaskWithoutPlan() throws Exception {
        CodingTask task = taskService.createTask("Task without plan");

        mockMvc.perform(post("/api/ai/approve/" + task.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void shouldRejectExecutingUnapprovedTask() throws Exception {
        CodingTask task = taskService.createTask("Unapproved task");

        mockMvc.perform(post("/api/ai/execute/" + task.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Task must be approved before execution"));
    }
}
