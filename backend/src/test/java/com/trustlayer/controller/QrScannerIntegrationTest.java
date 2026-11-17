package com.trustlayer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end QR checks use dynamically generated QR PNGs rather than checked-in payload files.
 * They prove that decoded content is never echoed back or persisted in the public response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrScannerIntegrationTest {

    private static final String STRONG_PASSWORD = "Secure!Pass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedUserCanScanUpiQrWithoutLeakingPaymentValues() throws Exception {
        Session owner = authenticate("qr-owner");
        CsrfMaterial csrf = csrf();
        String payload = "upi://pay?pa=secret.receiver%40bank&pn=Alice%20Private&am=1234.50&cu=INR&tn=private-note&tr=ref-123";

        mockMvc.perform(multipart("/api/scanner/qr")
                        .file(qrPng(payload))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(multipart("/api/scanner/qr")
                        .file(qrPng(payload))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payloadType").value("UPI_PAYMENT"))
                .andExpect(jsonPath("$.data.riskClassification").value("LOW"))
                .andExpect(jsonPath("$.data.explanation.decodedPayloadStored").value(false))
                .andExpect(jsonPath("$.data.explanation.externalActionInitiated").value(false))
                .andReturn();

        String response = created.getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret.receiver", "Alice Private", "1234.50", "private-note", "ref-123");
        String scanId = responseAt(created, "/data/scanId");

        mockMvc.perform(get("/api/scanner/qr/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].scanId").value(Long.parseLong(scanId)))
                .andExpect(jsonPath("$.data.items[0].target").value("UPI payment request (recipient and payment details redacted)"));

        mockMvc.perform(get("/api/scanner/qr/{scanId}", scanId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageFormat").value("PNG"))
                .andExpect(jsonPath("$.data.limitations[0]").value(org.hamcrest.Matchers.containsString("did not start a payment")));

        Session other = authenticate("qr-other");
        mockMvc.perform(get("/api/scanner/qr/{scanId}", scanId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
    }

    @Test
    void staticUrlRulesAreReusedForAUrlEncodedInQrAndUnsupportedUploadsAreRejected() throws Exception {
        Session owner = authenticate("qr-url");
        CsrfMaterial csrf = csrf();
        String payload = "http://user:super-secret@192.0.2.1/login?token=private-token&redirect=https%3A%2F%2Fevil.test";

        MvcResult created = mockMvc.perform(multipart("/api/scanner/qr")
                        .file(qrPng(payload))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payloadType").value("URL"))
                .andExpect(jsonPath("$.data.riskClassification").value("CRITICAL"))
                .andExpect(jsonPath("$.data.indicators").isNotEmpty())
                .andReturn();
        assertThat(created.getResponse().getContentAsString()).doesNotContain("super-secret", "private-token", "user:");

        MockMultipartFile textFile = new MockMultipartFile(
                "image", "not-a-qr.txt", MediaType.TEXT_PLAIN_VALUE, "not an image".getBytes()
        );
        mockMvc.perform(multipart("/api/scanner/qr")
                        .file(textFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header(csrf.headerName(), csrf.token())
                        .cookie(csrf.cookie()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_QR_IMAGE"));
    }

    private MockMultipartFile qrPng(String payload) throws WriterException, IOException {
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 320, 320);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", bytes);
            return new MockMultipartFile("image", "trusted-input-name-is-ignored.png", MediaType.IMAGE_PNG_VALUE, bytes.toByteArray());
        }
    }

    private Session authenticate(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", STRONG_PASSWORD,
                                "firstName", "QR",
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
