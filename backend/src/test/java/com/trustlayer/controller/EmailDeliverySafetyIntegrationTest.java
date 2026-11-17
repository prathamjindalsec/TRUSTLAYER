package com.trustlayer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prevents a dangerous half-implementation where production silently creates accounts that can
 * never receive an email verification or reset message.
 */
@SpringBootTest(properties = "app.security.development.expose-action-tokens=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailDeliverySafetyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void actionTokenWorkflowsFailClosedWhenNoDeliveryAdapterExists() throws Exception {
        String email = "mail-safety+" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Secure!Pass123",
                                "firstName", "Mail",
                                "lastName", "Safety"
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNAVAILABLE"));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNAVAILABLE"));
    }

    private String json(Map<String, String> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }
}
