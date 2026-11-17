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
 * Proves that dashboard values are assembled from persisted records, owner-scoped where private,
 * and redacted before they reach the browser contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {

    private static final String STRONG_PASSWORD = "Secure!Pass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dashboardUsesRealOwnerScopedDataAndDoesNotLeakPrivateInputs() throws Exception {
        Session owner = authenticate("dashboard-owner");
        CsrfMaterial csrf = csrf();
        String privateUrl = "http://private-user:private-password@192.0.2.1/login?token=private-url-token";
        String privateBeneficiary = "private-beneficiary@bank";
        String privateReportUrl = "https://paypa1-security.example.zip/login?token=private-report-token";

        createUrlScan(owner, csrf, privateUrl);
        createTransaction(owner, csrf, privateBeneficiary);
        createThreatReport(owner, csrf, privateReportUrl);

        Session otherUser = authenticate("dashboard-other");
        createUrlScan(otherUser, csrf, "https://paypa1-other.example.zip/login");

        MvcResult result = mockMvc.perform(get("/api/dashboard/statistics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUrlScans").value(1))
                .andExpect(jsonPath("$.data.reviewRecommendedUrlScans").value(1))
                .andExpect(jsonPath("$.data.totalQrScans").value(0))
                .andExpect(jsonPath("$.data.totalSimulatedTransactions").value(1))
                .andExpect(jsonPath("$.data.totalCommunityThreats").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totalCommunityReports").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.riskDistribution[?(@.riskClassification == 'HIGH')].count").value(1))
                .andExpect(jsonPath("$.data.recentActivity[?(@.activityType == 'URL_SCAN')]").isNotEmpty())
                .andExpect(jsonPath("$.data.recentActivity[?(@.activityType == 'TRANSACTION_SIMULATION')]").isNotEmpty())
                .andExpect(jsonPath("$.data.recentActivity[?(@.activityType == 'COMMUNITY_REPORT')]").isNotEmpty())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain(
                "private-user", "private-password", "private-url-token", "private-beneficiary", "private-report-token"
        );

        mockMvc.perform(get("/api/dashboard/statistics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUser.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUrlScans").value(1));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/statistics"))
                .andExpect(status().isUnauthorized());
    }

    private void createUrlScan(Session session, CsrfMaterial csrf, String url) throws Exception {
        mockMvc.perform(post("/api/scanner/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", url)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated());
    }

    private void createTransaction(Session session, CsrfMaterial csrf, String beneficiary) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", "1000.00");
        request.put("currency", "INR");
        request.put("beneficiaryIdentifier", beneficiary);
        request.put("transactionTime", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString());

        mockMvc.perform(post("/api/transactions/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated());
    }

    private void createThreatReport(Session session, CsrfMaterial csrf, String indicatorValue) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("indicatorType", "URL");
        request.put("indicatorValue", indicatorValue);
        request.put("category", "PHISHING");
        request.put("description", "Observed a suspicious payment lure.");

        mockMvc.perform(post("/api/threats/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated());
    }

    private Session authenticate(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", STRONG_PASSWORD,
                                "firstName", "Dashboard",
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
