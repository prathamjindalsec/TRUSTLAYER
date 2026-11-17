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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the public authentication contract against a real Spring Security filter chain.
 * H2 is used only as an isolated test database; MySQL schema compatibility is covered by the
 * opt-in MysqlSchemaMigrationIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    private static final String STRONG_PASSWORD = "Secure!Pass123";
    private static final String REPLACEMENT_PASSWORD = "Replaced!Pass456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationVerificationProfileCsrfRefreshAndLogoutAreEnforced() throws Exception {
        String email = uniqueEmail("ada");
        String verificationToken = register(email, STRONG_PASSWORD, "Ada", "Lovelace");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", STRONG_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult login = login(email, STRONG_PASSWORD);
        String accessToken = responseData(login, "accessToken");
        String refreshToken = responseCookie(login, AuthController.REFRESH_COOKIE_NAME).getValue();

        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/users/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].current").value(true))
                .andExpect(jsonPath("$.data.items[0].sessionId").isNotEmpty());

        mockMvc.perform(get("/api/users/login-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventType").value("AUTH.LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].outcome").value("SUCCESS"));

        CsrfMaterial csrf = csrf();
        mockMvc.perform(put("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "Grace", "lastName", "Hopper")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "Grace", "lastName", "Hopper")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Grace"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, refreshToken)))
                .andExpect(status().isForbidden());

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .header(csrf.headerName(), csrf.token())
                        .cookie(
                                csrf.cookie(),
                                new Cookie(AuthController.REFRESH_COOKIE_NAME, refreshToken)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String rotatedRefreshToken = responseCookie(refreshed, AuthController.REFRESH_COOKIE_NAME).getValue();
        mockMvc.perform(post("/api/auth/logout")
                        .header(csrf.headerName(), csrf.token())
                        .cookie(
                                csrf.cookie(),
                                new Cookie(AuthController.REFRESH_COOKIE_NAME, rotatedRefreshToken)
                        ))
                .andExpect(status().isNoContent());

        // The bearer token is session-bound, so logout invalidates it immediately rather than
        // merely waiting for its short JWT expiry time.
        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUsersCanRevokeAnIndividualActiveSession() throws Exception {
        String email = uniqueEmail("session");
        String verificationToken = register(email, STRONG_PASSWORD, "Session", "Owner");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        MvcResult login = login(email, STRONG_PASSWORD);
        String accessToken = responseData(login, "accessToken");
        MvcResult sessions = mockMvc.perform(get("/api/users/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = responseAt(sessions, "/data/items/0/sessionId");
        CsrfMaterial csrf = csrf();

        mockMvc.perform(delete("/api/users/sessions/{sessionId}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        MvcResult revokedSession = mockMvc.perform(delete("/api/users/sessions/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(responseCookie(revokedSession, AuthController.REFRESH_COOKIE_NAME).getMaxAge()).isZero();

        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllRequiresAnAccessTokenAndInvalidatesAllSessions() throws Exception {
        String email = uniqueEmail("all-sessions");
        String verificationToken = register(email, STRONG_PASSWORD, "All", "Sessions");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        MvcResult login = login(email, STRONG_PASSWORD);
        String accessToken = responseData(login, "accessToken");
        CsrfMaterial csrf = csrf();

        mockMvc.perform(post("/api/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsBeyondBcryptsEffectiveInputLimitAreRejected() throws Exception {
        String tooLongForBcrypt = "A1!" + "a".repeat(70);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", uniqueEmail("password-limit"),
                                "password", tooLongForBcrypt,
                                "firstName", "Password",
                                "lastName", "Limit"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void loginRejectsAPasswordThatOnlyDiffersAfterBcryptsInputLimit() throws Exception {
        String email = uniqueEmail("bcrypt-login");
        String verificationToken = register(email, STRONG_PASSWORD, "Bcrypt", "Login");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        String overlongAttempt = STRONG_PASSWORD + "x".repeat(72);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", overlongAttempt))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void passwordResetAndAccountDeletionRevokeActiveSessions() throws Exception {
        String email = uniqueEmail("lin");
        String verificationToken = register(email, STRONG_PASSWORD, "Lin", "Zhang");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        MvcResult login = login(email, STRONG_PASSWORD);
        String firstAccessToken = responseData(login, "accessToken");

        MvcResult forgotPassword = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted())
                .andReturn();
        String resetToken = responseData(forgotPassword, "developmentActionToken");
        assertThat(resetToken).isNotBlank();

        MvcResult reset = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken, "newPassword", REPLACEMENT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(responseCookie(reset, AuthController.REFRESH_COOKIE_NAME).getMaxAge()).isZero();

        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", STRONG_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        MvcResult secondLogin = login(email, REPLACEMENT_PASSWORD);
        String secondAccessToken = responseData(secondLogin, "accessToken");
        CsrfMaterial csrf = csrf();

        MvcResult deletedAccount = mockMvc.perform(delete("/api/users/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", REPLACEMENT_PASSWORD)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken)
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(responseCookie(deletedAccount, AuthController.REFRESH_COOKIE_NAME).getMaxAge()).isZero();

        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken))
                .andExpect(status().isUnauthorized());
    }

    private String register(String email, String password, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", password,
                                "firstName", firstName,
                                "lastName", lastName
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.status").value("PENDING_VERIFICATION"))
                .andReturn();

        String verificationToken = responseData(result, "developmentActionToken");
        assertThat(verificationToken).isNotBlank();
        return verificationToken;
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
    }

    private CsrfMaterial csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        String token = responseData(result, "token");
        String headerName = responseData(result, "headerName");
        Cookie cookie = responseCookie(result, "XSRF-TOKEN");
        return new CsrfMaterial(token, headerName, cookie);
    }

    private Cookie responseCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie)
                .as("response cookie %s", name)
                .isNotNull();
        return cookie;
    }

    private String responseData(MvcResult result, String fieldName) throws Exception {
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return document.path("data").path(fieldName).asText();
    }

    private String responseAt(MvcResult result, String jsonPointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).at(jsonPointer).asText();
    }

    private String json(Map<String, String> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.test";
    }

    private record CsrfMaterial(String token, String headerName, Cookie cookie) {
    }
}
