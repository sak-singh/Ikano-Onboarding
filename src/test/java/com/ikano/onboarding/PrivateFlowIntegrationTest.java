package com.ikano.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PrivateFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createPrivateSessionAndSubmitIdentityStep() throws Exception {
        String createBody = """
                {"country":"SWEDEN","customerType":"PRIVATE"}
                """;

        String createResponse = mvc.perform(post("/api/private/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String sessionId = createJson.get("sessionId").asText();
        assertThat(sessionId).isNotBlank();

        String stepBody = """
                {"answers":{"fullName":"John Doe","personalIdNumber":"19900101-1234"}}
                """;

        mvc.perform(post("/api/private/sessions/{id}/steps/identity", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepBody))
                .andExpect(status().isOk());

        mvc.perform(get("/api/private/sessions/{id}/review", sessionId))
                .andExpect(status().isOk());
    }
}

