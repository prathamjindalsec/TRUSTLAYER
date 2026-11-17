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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that an extension must receive a user-granted, PKCE-bound, short-lived scan credential;
 * it cannot reuse browser tokens or call the wider browser API with that credential.
 */
@SpringBootTest(properties = {
        "app.extension.enabled=true",
        "app.extension.client-id=trustlayer-chrome-extension",
        "app.extension.redirect-uri=https://testextensionid.chromiumapp.org/trustlayer",
        "app.extension.origin=chrome-extension://testextensionid",
        "app.extension.authorization-code-ttl=PT1M",
        "app.extension.access-token-ttl=PT5M"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExtensionAuthorizationIntegrationTest {

    private static final String STRONG_PASSWORD = "Secure!Pass123";
    private static final String CLIENT_ID = "trustlayer-chrome-extension";
    private static final String REDIRECT_URI = "https://testextensionid.chromiumapp.org/trustlayer";
    private static final String EXTENSION_ORIGIN = "chrome-extension://testextensionid";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pkceBoundExtensionTokenCanOnlyCallItsScopedUrlScanEndpoint() throws Exception {
        Session browserSession = authenticate("extension-owner");
        CsrfMaterial csrf = csrf();
        String verifier = "a".repeat(43);

        MvcResult authorization = mockMvc.perform(post("/api/extension/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientId", CLIENT_ID,
                                "redirectUri", REDIRECT_URI,
                                "codeChallenge", s256Challenge(verifier),
                                "codeChallengeMethod", "S256",
                                "state", "extension-state-" + UUID.randomUUID()
                        )))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + browserSession.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("url:scan"))
                .andReturn();

        String code = responseAt(authorization, "/data/authorizationCode");
        MvcResult exchange = mockMvc.perform(post("/api/extension/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientId", CLIENT_ID,
                                "redirectUri", REDIRECT_URI,
                                "code", code,
                                "codeVerifier", verifier
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("url:scan"))
                .andReturn();

        String extensionAccessToken = responseAt(exchange, "/data/accessToken");
        mockMvc.perform(post("/api/extension/scan-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://paypa1-extension.example.zip/login")))
                        .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + extensionAccessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        CsrfMaterial scannerCsrf = csrf();
        mockMvc.perform(post("/api/scanner/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://paypa1-extension.example.zip/login")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + extensionAccessToken)
                        .header(scannerCsrf.headerName(), scannerCsrf.token())
                        .cookie(scannerCsrf.cookie()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/extension/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientId", CLIENT_ID,
                                "redirectUri", REDIRECT_URI,
                                "code", code,
                                "codeVerifier", verifier
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_EXTENSION_AUTHORIZATION"));
    }

    @Test
    void extensionCorsDoesNotGrantArbitraryOrigins() throws Exception {
        mockMvc.perform(options("/api/extension/scan-url")
                        .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION_ORIGIN));

        mockMvc.perform(options("/api/extension/scan-url")
                        .header(HttpHeaders.ORIGIN, "chrome-extension://another-extension")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private Session authenticate(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", STRONG_PASSWORD,
                                "firstName", "Extension",
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
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new CsrfMaterial(
                responseAt(result, "/data/token"),
                responseAt(result, "/data/headerName"),
                cookie
        );
    }

    private String s256Challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
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
