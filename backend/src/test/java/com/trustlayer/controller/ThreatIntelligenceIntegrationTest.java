package com.trustlayer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustlayer.repository.ThreatReportRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that report frequency is handled as unverified corroboration, not as a fraud verdict,
 * and that a raw indicator secret never appears in a public intelligence response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ThreatIntelligenceIntegrationTest {

    private static final String STRONG_PASSWORD = "Secure!Pass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ThreatReportRepository threatReportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void independentReportsAreCorroborationButRemainUnverifiedAndPrivacySafe() throws Exception {
        Session firstReporter = authenticate("threat-reporter-one");
        Session secondReporter = authenticate("threat-reporter-two");
        Session thirdReporter = authenticate("threat-reporter-three");
        CsrfMaterial csrf = csrf();
        String rawUrl = "https://paypa1-security.example.zip/login?token=private-token";
        Map<String, Object> report = reportPayload(
                "URL", rawUrl, "PHISHING", "Someone asked for an urgent payment using " + rawUrl
        );

        mockMvc.perform(post("/api/threats/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(report))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstReporter.accessToken()))
                .andExpect(status().isForbidden());

        MvcResult first = report(firstReporter, csrf, report)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdNewThreat").value(true))
                .andExpect(jsonPath("$.data.corroboratesExistingThreat").value(false))
                .andExpect(jsonPath("$.data.threat.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.threat.communityReportCount").value(1))
                .andReturn();
        String threatId = responseAt(first, "/data/threat/threatId");
        String reportId = responseAt(first, "/data/reportId");
        assertThat(first.getResponse().getContentAsString()).doesNotContain("private-token");
        assertThat(threatReportRepository.findById(Long.parseLong(reportId)).orElseThrow().getDescription())
                .doesNotContain("private-token")
                .contains("[redacted");

        report(firstReporter, csrf, report)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REPORT"));

        report(secondReporter, csrf, report)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdNewThreat").value(false))
                .andExpect(jsonPath("$.data.corroboratesExistingThreat").value(true))
                .andExpect(jsonPath("$.data.threat.communityReportCount").value(2))
                .andExpect(jsonPath("$.data.threat.verificationStatus").value("UNVERIFIED"));

        report(thirdReporter, csrf, report)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.threat.communityReportCount").value(3))
                .andExpect(jsonPath("$.data.threat.repeatedCampaignCandidate").value(true))
                .andExpect(jsonPath("$.data.threat.verificationNotice").value(org.hamcrest.Matchers.containsString("unverified")));

        MvcResult search = mockMvc.perform(get("/api/threats")
                        .param("type", "URL")
                        .param("query", rawUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstReporter.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].threatId").value(Long.parseLong(threatId)))
                .andExpect(jsonPath("$.data.items[0].verificationStatus").value("UNVERIFIED"))
                .andReturn();
        assertThat(search.getResponse().getContentAsString()).doesNotContain("private-token");

        mockMvc.perform(get("/api/threats/{threatId}", threatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondReporter.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communityReportCount").value(3));

        MvcResult statistics = mockMvc.perform(get("/api/threats/statistics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstReporter.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(statistics.getResponse().getContentAsString()).doesNotContain("private-token");
        JsonNode statisticsData = objectMapper.readTree(statistics.getResponse().getContentAsByteArray()).at("/data");
        assertThat(statisticsData.path("totalCanonicalThreats").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(statisticsData.path("totalCommunityReports").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(statisticsData.path("unverifiedThreats").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(containsThreat(statisticsData.path("repeatedCampaignCandidates"), threatId, 3)).isTrue();

        MvcResult campaigns = mockMvc.perform(get("/api/threats/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstReporter.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(containsThreat(
                objectMapper.readTree(campaigns.getResponse().getContentAsByteArray()).at("/data"), threatId, 3
        )).isTrue();
    }

    @Test
    void phoneNumbersAreSearchableByExactInputButAreDisplayedRedacted() throws Exception {
        Session reporter = authenticate("threat-phone");
        CsrfMaterial csrf = csrf();
        String phone = "+91 98765 43210";
        MvcResult created = report(reporter, csrf, reportPayload("PHONE", phone, "PAYMENT_SCAM", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.threat.indicator").value("Phone number ending 3210"))
                .andReturn();
        assertThat(created.getResponse().getContentAsString()).doesNotContain("98765");

        mockMvc.perform(get("/api/threats")
                        .param("type", "PHONE")
                        .param("query", phone)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].indicator").value("Phone number ending 3210"));
    }

    @Test
    void onlyAnActualDatabaseAdminCanVerifyACommunityThreat() throws Exception {
        Session reporter = authenticate("threat-review-reporter");
        Session moderator = authenticate("threat-review-admin");
        CsrfMaterial csrf = csrf();
        MvcResult created = report(reporter, csrf, reportPayload(
                "DOMAIN", "review-target.example", "IMPERSONATION", "Needs human review."
        )).andExpect(status().isCreated()).andReturn();
        String threatId = responseAt(created, "/data/threat/threatId");
        String body = json(Map.of("verificationStatus", "VERIFIED"));

        mockMvc.perform(patch("/api/admin/threats/{threatId}/verification", threatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isForbidden());

        // Self-registration can never grant ADMIN. This is test-only database setup proving
        // that authorization is evaluated from the current account record, not a caller input.
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", moderator.email());

        mockMvc.perform(patch("/api/admin/threats/{threatId}/verification", threatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderator.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.confidenceScore").value(100));
    }

    private org.springframework.test.web.servlet.ResultActions report(
            Session session,
            CsrfMaterial csrf,
            Map<String, Object> report
    ) throws Exception {
        return mockMvc.perform(post("/api/threats/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(report))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .header(csrf.headerName(), csrf.token())
                .cookie(csrf.cookie()));
    }

    private Map<String, Object> reportPayload(String type, String indicatorValue, String category, String description) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("indicatorType", type);
        request.put("indicatorValue", indicatorValue);
        request.put("category", category);
        if (description != null) {
            request.put("description", description);
        }
        return request;
    }

    private Session authenticate(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", STRONG_PASSWORD,
                                "firstName", "Threat",
                                "lastName", "Reporter"
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
        return new Session(email, responseAt(login, "/data/accessToken"));
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

    private boolean containsThreat(JsonNode threats, String threatId, int reportCount) {
        for (JsonNode threat : threats) {
            if (threatId.equals(threat.path("threatId").asText())
                    && threat.path("communityReportCount").asInt() == reportCount) {
                return true;
            }
        }
        return false;
    }

    private record Session(String email, String accessToken) {
    }

    private record CsrfMaterial(String token, String headerName, Cookie cookie) {
    }
}
