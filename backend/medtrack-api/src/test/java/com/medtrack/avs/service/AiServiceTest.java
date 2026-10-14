package com.medtrack.avs.service;

import com.medtrack.avs.config.AiProperties;
import com.medtrack.avs.dto.AiChatRequest;
import com.medtrack.avs.dto.AiChatResponse;
import com.medtrack.avs.exception.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceTest {

    private AiProperties aiProperties;
    private MockRestServiceServer mockServer;
    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        aiService = new AiServiceImpl(aiProperties, restClient);
    }

    @Test
    @DisplayName("1. isConfigured returns false when apiKey is empty or null")
    void testIsConfigured_emptyKey() {
        aiProperties.setApiKey("");
        assertFalse(aiService.isConfigured());

        aiProperties.setApiKey("   ");
        assertFalse(aiService.isConfigured());

        aiProperties.setApiKey(null);
        assertFalse(aiService.isConfigured());
    }

    @Test
    @DisplayName("2. isConfigured returns false when apiKey is a placeholder")
    void testIsConfigured_placeholderKey() {
        aiProperties.setApiKey("placeholder");
        assertFalse(aiService.isConfigured());

        aiProperties.setApiKey("CHANGE_ME");
        assertFalse(aiService.isConfigured());

        aiProperties.setApiKey("CHANGE_ME_AI_API_KEY");
        assertFalse(aiService.isConfigured());
    }

    @Test
    @DisplayName("3. isConfigured returns true when a valid key is provided")
    void testIsConfigured_validKey() {
        aiProperties.setApiKey("ai-secret-key-12345");
        assertTrue(aiService.isConfigured());
    }

    @Test
    @DisplayName("4. chat() throws AiProviderException(AI_NOT_CONFIGURED, 503) when not configured")
    void testChat_whenUnconfigured_throwsException() {
        aiProperties.setApiKey("");
        AiChatRequest request = new AiChatRequest("Where is asset ECG-001?");

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                aiService.chat(request, "nurse@medtrack.org"));

        assertEquals("AI_NOT_CONFIGURED", ex.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("AI provider is not configured"));
    }

    @Test
    @DisplayName("5. chat() with Gemini succeeds and returns structured response")
    void testChat_geminiSuccess() {
        aiProperties.setProvider("GEMINI");
        aiProperties.setApiKey("real-gemini-key");
        aiProperties.setModel("gemini-1.5-flash");

        String geminiJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Asset ECG-001 is currently in Cardiology Ward." }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "real-gemini-key"))
                .andRespond(withSuccess(geminiJson, MediaType.APPLICATION_JSON));

        AiChatRequest request = new AiChatRequest("Where is asset ECG-001 currently reported?");
        AiChatResponse response = aiService.chat(request, "admin@medtrack.org");

        assertNotNull(response);
        assertEquals("Asset ECG-001 is currently in Cardiology Ward.", response.getAnswer());
        assertEquals("GEMINI", response.getProvider());
        assertEquals("gemini-1.5-flash", response.getModel());
        assertNotNull(response.getAnsweredAt());
        assertFalse(response.isCached());
        mockServer.verify();
    }

    @Test
    @DisplayName("6. chat() with OpenAI succeeds and returns structured response")
    void testChat_openAiSuccess() {
        aiProperties.setProvider("OPENAI");
        aiProperties.setApiKey("sk-real-openai-key");
        aiProperties.setModel("gpt-4o-mini");

        String openAiJson = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "There are 2 active transfers in transit."
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-real-openai-key"))
                .andRespond(withSuccess(openAiJson, MediaType.APPLICATION_JSON));

        AiChatRequest request = new AiChatRequest("Summarize recent transfers.");
        AiChatResponse response = aiService.chat(request, "supervisor@medtrack.org");

        assertNotNull(response);
        assertEquals("There are 2 active transfers in transit.", response.getAnswer());
        assertEquals("OPENAI", response.getProvider());
        assertEquals("gpt-4o-mini", response.getModel());
        mockServer.verify();
    }

    @Test
    @DisplayName("7. Provider timeout throws AiProviderException(AI_PROVIDER_TIMEOUT, 504)")
    void testChat_providerTimeout() {
        aiProperties.setProvider("GEMINI");
        aiProperties.setApiKey("test-key");

        mockServer.expect(requestTo(containsString("generateContent")))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        AiChatRequest request = new AiChatRequest("Timeout test question");

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                aiService.chat(request, "staff@medtrack.org"));

        assertEquals("AI_PROVIDER_TIMEOUT", ex.getErrorCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("8. Provider 500 error throws AiProviderException(AI_PROVIDER_ERROR, 503) without leaking secrets")
    void testChat_providerError_doesNotLeakSecret() {
        String secretKey = "super-secret-production-key-998877";
        aiProperties.setProvider("OPENAI");
        aiProperties.setApiKey(secretKey);

        mockServer.expect(requestTo(containsString("chat/completions")))
                .andRespond(withServerError());

        AiChatRequest request = new AiChatRequest("Will this fail gracefully?");

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                aiService.chat(request, "admin@medtrack.org"));

        assertEquals("AI_PROVIDER_ERROR", ex.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        // Verify secret key is nowhere in message
        assertFalse(ex.getMessage().contains(secretKey));
    }

    @Test
    @DisplayName("9. Unsupported provider throws AiProviderException(UNSUPPORTED_AI_PROVIDER)")
    void testChat_unsupportedProvider() {
        aiProperties.setProvider("UNKNOWN_PROVIDER");
        aiProperties.setApiKey("some-key");

        AiChatRequest request = new AiChatRequest("Hello");

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                aiService.chat(request, "admin@medtrack.org"));

        assertEquals("UNSUPPORTED_AI_PROVIDER", ex.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    @DisplayName("10. Transient 503 error performs bounded retry with backoff and succeeds")
    void testChat_transient503_retriesAndSucceeds() {
        aiProperties.setProvider("GEMINI");
        aiProperties.setApiKey("test-gemini-key");
        aiProperties.setModel("gemini-3.5-flash-lite");
        aiProperties.setMaxRetries(1);
        aiProperties.setRetryBackoffMs(10);

        String geminiJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Asset ECG-001 is in Cardiology ICU." }
                        ]
                      }
                    }
                  ]
                }
                """;

        // First attempt returns 503, second attempt succeeds
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(geminiJson, MediaType.APPLICATION_JSON));

        AiChatRequest request = new AiChatRequest("Where is asset ECG-001 currently reported?");
        AiChatResponse response = aiService.chat(request, "admin@medtrack.org");

        assertNotNull(response);
        assertEquals("Asset ECG-001 is in Cardiology ICU.", response.getAnswer());
        assertEquals("gemini-3.5-flash-lite", response.getModel());
        mockServer.verify();
    }

    @Test
    @DisplayName("11. Overloaded primary model fails over automatically to configured fallback model")
    void testChat_primary503_failsOverToFallbackModel() {
        aiProperties.setProvider("GEMINI");
        aiProperties.setApiKey("test-gemini-key");
        aiProperties.setModel("gemini-3.8-flash");
        aiProperties.setFallbackModels("gemini-3.5-flash-lite");
        aiProperties.setMaxRetries(0); // Fail primary on first attempt to trigger fallback

        String fallbackJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Asset ECG-001 reported at Cardiology Ward from fallback model." }
                        ]
                      }
                    }
                  ]
                }
                """;

        // Primary model returns 503
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // Fallback model responds with 200 OK
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fallbackJson, MediaType.APPLICATION_JSON));

        AiChatRequest request = new AiChatRequest("Where is asset ECG-001 currently reported?");
        AiChatResponse response = aiService.chat(request, "admin@medtrack.org");

        assertNotNull(response);
        assertEquals("Asset ECG-001 reported at Cardiology Ward from fallback model.", response.getAnswer());
        assertEquals("gemini-3.5-flash-lite", response.getModel());
        mockServer.verify();
    }

    @Test
    @DisplayName("12. When all candidate models fail with 503, throws AiProviderException(AI_PROVIDER_UNAVAILABLE, 503)")
    void testChat_allModelsFail503_throwsProviderUnavailable() {
        aiProperties.setProvider("GEMINI");
        aiProperties.setApiKey("test-gemini-key");
        aiProperties.setModel("gemini-3.8-flash");
        aiProperties.setFallbackModels("gemini-3.5-flash-lite");
        aiProperties.setMaxRetries(0);

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        AiChatRequest request = new AiChatRequest("Where is asset ECG-001 currently reported?");

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                aiService.chat(request, "admin@medtrack.org"));

        assertEquals("AI_PROVIDER_UNAVAILABLE", ex.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        mockServer.verify();
    }
}
