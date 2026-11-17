package com.trustlayer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the simulation-only API uses prior demo data for behavioral signals without storing or
 * returning a raw beneficiary identifier. No external payment integration exists in this test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

    private static final String STRONG_PASSWORD = "Secure!Pass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userCanAnalyzeSimulatedPatternWithoutLeakingBeneficiaryOrAllowingIdor() throws Exception {
        Session owner = authenticate("transaction-owner");
        CsrfMaterial csrf = csrf();
        String sensitiveBeneficiary = "private-person@bank-example";
        OffsetDateTime baselineTime = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).minusDays(2).withHour(11).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime unusualTime = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).minusDays(1).withHour(2).withMinute(30).withSecond(0).withNano(0);

        Map<String, Object> baseline = transactionPayload("1000.00", sensitiveBeneficiary, baselineTime);
        mockMvc.perform(post("/api/transactions/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(baseline))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/transactions/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(baseline))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.riskClassification").value("LOW"));

        MvcResult elevated = mockMvc.perform(post("/api/transactions/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(transactionPayload("120000.00", sensitiveBeneficiary, unusualTime)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.riskClassification").value("HIGH"))
                .andExpect(jsonPath("$.data.confidenceLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.beneficiary").value("Beneficiary identifier (redacted)"))
                .andExpect(jsonPath("$.data.explanation.simulationOnly").value(true))
                .andExpect(jsonPath("$.data.indicators[?(@.code == 'AMOUNT_ABOVE_BENEFICIARY_BASELINE')]").isNotEmpty())
                .andReturn();

        String response = elevated.getResponse().getContentAsString();
        assertThat(response).doesNotContain(sensitiveBeneficiary);
        String transactionId = responseAt(elevated, "/data/transactionId");

        mockMvc.perform(get("/api/transactions/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(Long.parseLong(transactionId)))
                .andExpect(jsonPath("$.data.items[0].beneficiary").value("Beneficiary identifier (redacted)"));

        mockMvc.perform(get("/api/transactions/{transactionId}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limitations[0]").value(org.hamcrest.Matchers.containsString("Simulation only")));

        Session other = authenticate("transaction-other");
        mockMvc.perform(get("/api/transactions/{transactionId}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void invalidOrFutureSimulationInputIsRejected() throws Exception {
        Session owner = authenticate("transaction-validation");
        CsrfMaterial csrf = csrf();
        Map<String, Object> future = transactionPayload(
                "0.01", "someone@bank", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
        );

        mockMvc.perform(post("/api/transactions/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(future))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private Map<String, Object> transactionPayload(String amount, String beneficiary, OffsetDateTime transactionTime) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amount);
        payload.put("currency", "INR");
        payload.put("beneficiaryIdentifier", beneficiary);
        payload.put("transactionTime", transactionTime.toString());
        return payload;
    }

    private Session authenticate(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", STRONG_PASSWORD,
                                "firstName", "Transaction",
                                "lastName", "Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String verificationToken = responseAt(registration, "/data/developmentActionToken");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(responseAt(login, "/data/accessToken"));
    }

    private CsrfMaterial csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new CsrfMaterial(
                responseAt(result, "/data/token"),
                responseAt(result, "/data/headerName"),
                responseCookie(result, "XSRF-TOKEN")
        );
    }

    private Cookie responseCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("response cookie %s", name).isNotNull();
        return cookie;
    }

    private String responseAt(MvcResult result, String pointer) throws Exception {
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return document.at(pointer).asText();
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private record Session(String accessToken) {
    }

    private record CsrfMaterial(String token, String headerName, Cookie cookie) {
    }
}
