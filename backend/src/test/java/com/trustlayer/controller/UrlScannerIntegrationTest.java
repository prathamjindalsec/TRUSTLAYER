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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises URL persistence, authenticated ownership, CSRF enforcement, and privacy redaction
 * through the full Spring Security chain. H2 is only the isolated test store.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlScannerIntegrationTest {

    private static final String STRONG_PASSWORD = "Secure!Pass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedUserCanPersistAndReadAnExplainableUrlScanWithoutLeakingSecrets() throws Exception {
        Session session = authenticate("scanner-owner");
        CsrfMaterial csrf = csrf();
        String submittedUrl = "http://user:super-secret@192.0.2.1/reset/password?token=private-token&redirect=https%3A%2F%2Fevil.test";

        mockMvc.perform(post("/api/scanner/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", submittedUrl)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/scanner/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", submittedUrl)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.riskClassification").value("CRITICAL"))
                .andExpect(jsonPath("$.data.indicators").isNotEmpty())
                .andExpect(jsonPath("$.data.explanation.confirmedMaliciousIndicatorCount").value(0))
                .andReturn();

        String response = created.getResponse().getContentAsString();
        assertThat(response).doesNotContain("super-secret", "private-token", "user:");
        String scanId = responseAt(created, "/data/scanId");

        mockMvc.perform(get("/api/scanner/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].scanId").value(Long.parseLong(scanId)))
                .andExpect(jsonPath("$.data.items[0].targetUrl").value("http://192.0.2.1/[path-redacted]?[query-redacted]"));

        mockMvc.perform(get("/api/scanner/{scanId}", scanId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanId").value(Long.parseLong(scanId)))
                .andExpect(jsonPath("$.data.limitations[0]").value(org.hamcrest.Matchers.containsString("did not connect")));

        mockMvc.perform(post("/api/scanner/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "ftp://example.com/file")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }

    @Test
    void scanDetailsAreNotAccessibleToAnotherAuthenticatedUser() throws Exception {
        Session owner = authenticate("scan-owner");
        CsrfMaterial ownerCsrf = csrf();
        MvcResult created = mockMvc.perform(post("/api/scanner/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://paypa1-secure.example.zip/login")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header(ownerCsrf.headerName(), ownerCsrf.token())
                        .cookie(ownerCsrf.cookie()))
                .andExpect(status().isCreated())
                .andReturn();
        String scanId = responseAt(created, "/data/scanId");

        Session otherUser = authenticate("scan-other");
        mockMvc.perform(get("/api/scanner/{scanId}", scanId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUser.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
    }

    private Session authenticate(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", STRONG_PASSWORD,
                                "firstName", "Scan",
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

    private String json(Map<String, String> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private record Session(String accessToken) {
    }

    private record CsrfMaterial(String token, String headerName, Cookie cookie) {
    }
}
