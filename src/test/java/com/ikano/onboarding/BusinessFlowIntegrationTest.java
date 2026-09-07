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
class BusinessFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBusinessSessionAndSubmitCompanyStep() throws Exception {
        String createBody = """
                {"country":"SWEDEN","customerType":"BUSINESS"}
                """;

        String createResponse = mvc.perform(post("/api/business/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String sessionId = createJson.get("sessionId").asText();
        assertThat(sessionId).isNotBlank();

        String companyStepBody = """
                {"answers":{"organisationNumber":"556677-8899","legalName":"Acme AB","legalForm":"AB"}}
                """;

        mvc.perform(post("/api/business/sessions/{id}/steps/company", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyStepBody))
                .andExpect(status().isOk());

        mvc.perform(get("/api/business/sessions/{id}/review", sessionId))
                .andExpect(status().isOk());
    }
}

