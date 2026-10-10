package com.medtrack.avs.security;

import com.medtrack.avs.config.AiProperties;
import com.medtrack.avs.dto.AiChatRequest;
import com.medtrack.avs.dto.AiChatResponse;
import com.medtrack.avs.service.AiServiceImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SecurityPostVerificationCleanupTest {

    private static final String LEGACY_384BIT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Test
    @DisplayName("1. Algorithm Audit: JWTs are explicitly signed with HS384 and header reflects alg=HS384")
    void testJwtAlgorithm_explicitlySignsWithHs384() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", LEGACY_384BIT_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 86400000L);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("admin@medtrack.local")
                .password("securePassword")
                .roles("ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);

        // Inspect JOSE header (first segment before the first period)
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT must consist of header, payload, and signature");

        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        assertTrue(headerJson.contains("\"alg\":\"HS384\""),
                "JOSE header must explicitly declare alg as HS384: " + headerJson);

        // Verify token is accepted by parser
        assertTrue(jwtService.isTokenValid(token, userDetails));
        assertEquals("admin@medtrack.local", jwtService.extractUsername(token));
        List<String> roles = jwtService.extractRoles(token);
        assertTrue(roles.contains("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("2. Cryptographic Strength: HS384 requires >= 384 bits (48 bytes); weaker keys are rejected")
    void testJwtCryptographicStrength_rejectsWeakKeys() {
        // 256-bit (32-byte) key is insufficient for HS384 per RFC 7518
        byte[] weak256BitKey = new byte[32];
        for (int i = 0; i < 32; i++) weak256BitKey[i] = (byte) (i + 1);
        String weakSecret = Base64.getEncoder().encodeToString(weak256BitKey);

        JwtService weakJwtService = new JwtService();
        ReflectionTestUtils.setField(weakJwtService, "secretKey", weakSecret);
        ReflectionTestUtils.setField(weakJwtService, "jwtExpirationMs", 86400000L);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("admin@medtrack.local")
                .password("securePassword")
                .roles("ADMIN")
                .build();

        io.jsonwebtoken.security.SignatureException ex = assertThrows(
                io.jsonwebtoken.security.SignatureException.class,
                () -> weakJwtService.generateToken(userDetails),
                "JJWT must reject keys under 384 bits when signing with HS384"
        );
        assertTrue(ex.getMessage().contains("not secure enough for the HS384 algorithm") || ex.getCause() instanceof WeakKeyException,
                "Expected rejection due to key size smaller than 384 bits");

        // 384-bit (48-byte) key is cryptographically valid for HS384
        byte[] valid48ByteKey = new byte[48];
        for (int i = 0; i < 48; i++) valid48ByteKey[i] = (byte) (i + 42);
        String validSecret = Base64.getEncoder().encodeToString(valid48ByteKey);

        JwtService validJwtService = new JwtService();
        ReflectionTestUtils.setField(validJwtService, "secretKey", validSecret);
        ReflectionTestUtils.setField(validJwtService, "jwtExpirationMs", 86400000L);

        assertDoesNotThrow(() -> validJwtService.generateToken(userDetails));
    }

    @Test
    @DisplayName("3. Secret Rotation: Tokens signed by a previous key fail signature verification under the new key")
    void testJwtSecretRotation_rejectsCompromisedTokens() {
        JwtService oldJwtService = new JwtService();
        ReflectionTestUtils.setField(oldJwtService, "secretKey", LEGACY_384BIT_SECRET);
        ReflectionTestUtils.setField(oldJwtService, "jwtExpirationMs", 86400000L);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("admin@medtrack.local")
                .password("any-pass")
                .roles("ADMIN")
                .build();

        String tokenIssuedWithOldKey = oldJwtService.generateToken(userDetails);
        assertTrue(oldJwtService.isTokenValid(tokenIssuedWithOldKey, userDetails));

        // Rotated 384-bit key (48 bytes)
        byte[] newKeyBytes = new byte[48];
        for (int i = 0; i < 48; i++) {
            newKeyBytes[i] = (byte) (i + 99);
        }
        String newSecret = Base64.getEncoder().encodeToString(newKeyBytes);

        JwtService rotatedJwtService = new JwtService();
        ReflectionTestUtils.setField(rotatedJwtService, "secretKey", newSecret);
        ReflectionTestUtils.setField(rotatedJwtService, "jwtExpirationMs", 86400000L);

        // Verification: token signed with old key must fail validation under rotated key
        assertFalse(rotatedJwtService.isTokenValid(tokenIssuedWithOldKey, userDetails),
                "Tokens generated prior to rotation must be rejected");

        // Fresh token with new key succeeds
        String freshToken = rotatedJwtService.generateToken(userDetails);
        assertTrue(rotatedJwtService.isTokenValid(freshToken, userDetails),
                "Tokens generated after rotation must validate successfully");
    }

    @Test
    @DisplayName("4. Signature Validation: Tampered payloads and invalid signatures are rejected")
    void testJwtSignatureValidation_rejectsTamperedTokens() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", LEGACY_384BIT_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 86400000L);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("admin@medtrack.local")
                .password("any-pass")
                .roles("ADMIN")
                .build();

        String validToken = jwtService.generateToken(userDetails);
        String[] parts = validToken.split("\\.");

        // Tamper with payload (substituting "STAFF" for "ADMIN")
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayloadJson = payloadJson.replace("ROLE_ADMIN", "ROLE_SUPERUSER");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertFalse(jwtService.isTokenValid(tamperedToken, userDetails),
                "Tampered payload must fail signature verification");

        // Tamper with signature
        String corruptedSignatureToken = parts[0] + "." + parts[1] + ".corruptedSignatureXYZ123";
        assertFalse(jwtService.isTokenValid(corruptedSignatureToken, userDetails),
                "Tampered signature must fail verification");
    }

    @Test
    @DisplayName("5. Password Management: Accounts use independent hashes and salts without sharing")
    void testAccountPasswords_useIndependentHashesAndSalts() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();

        String pass1 = "AdminUniqueSecPass_1";
        String pass2 = "SupervisorUniqueSecPass_2";
        String pass3 = "StaffUniqueSecPass_3";

        String hash1 = encoder.encode(pass1);
        String hash2 = encoder.encode(pass2);
        String hash3 = encoder.encode(pass3);

        assertNotEquals(hash1, hash2);
        assertNotEquals(hash2, hash3);
        assertNotEquals(hash1, hash3);

        assertTrue(encoder.matches(pass1, hash1));
        assertFalse(encoder.matches(pass1, hash2));
        assertFalse(encoder.matches(pass1, hash3));

        assertTrue(encoder.matches(pass2, hash2));
        assertTrue(encoder.matches(pass3, hash3));
    }

    @Test
    @DisplayName("6. Gemini Request Authentication: Key is passed exclusively via x-goog-api-key header")
    void testGeminiRequest_usesHeaderAndNoQueryParam() {
        AiProperties properties = new AiProperties();
        properties.setProvider("GEMINI");
        properties.setModel("gemini-3.8-flash");
        properties.setApiKey("sec-clean-gemini-key-99");

        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        String geminiJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "All systems secure and operational." }
                        ]
                      }
                    }
                  ]
                }
                """;

        // Must NOT contain ?key= in URI
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(requestTo(not(containsString("?key="))))
                .andExpect(requestTo(not(containsString("sec-clean-gemini-key-99"))))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "sec-clean-gemini-key-99"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(geminiJson, MediaType.APPLICATION_JSON));

        RestClient restClient = restClientBuilder.build();
        AiServiceImpl aiService = new AiServiceImpl(properties, restClient);
        AiChatResponse response = aiService.chat(new AiChatRequest("Status check"), "admin@medtrack.local");

        assertNotNull(response);
        assertEquals("All systems secure and operational.", response.getAnswer());
        assertEquals("GEMINI", response.getProvider());
        mockServer.verify();
    }

    @Test
    @DisplayName("7. Fail-closed at startup: validateSecretKey rejects missing, empty, invalid Base64, and sub-384-bit secrets")
    void testJwtFailClosed_atStartupWhenSecretIsMissingOrInvalid() {
        JwtService jwtService = new JwtService();

        // Missing / null
        ReflectionTestUtils.setField(jwtService, "secretKey", null);
        assertThrows(IllegalStateException.class, jwtService::validateSecretKey,
                "Must fail closed when secretKey is null");

        // Empty / blank
        ReflectionTestUtils.setField(jwtService, "secretKey", "   ");
        assertThrows(IllegalStateException.class, jwtService::validateSecretKey,
                "Must fail closed when secretKey is empty");

        // Invalid Base64
        ReflectionTestUtils.setField(jwtService, "secretKey", "not*valid*base64!!!");
        assertThrows(IllegalStateException.class, jwtService::validateSecretKey,
                "Must fail closed when secretKey is not valid Base64");

        // Sub-384-bit key (32 bytes = 256 bits)
        byte[] sub384Key = new byte[32];
        String sub384Secret = Base64.getEncoder().encodeToString(sub384Key);
        ReflectionTestUtils.setField(jwtService, "secretKey", sub384Secret);
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtService::validateSecretKey,
                "Must fail closed when secretKey is less than 384 bits");
        assertTrue(ex.getMessage().contains("at least 384 bits"));

        // Valid 384-bit key (48 bytes)
        byte[] valid384Key = new byte[48];
        String valid384Secret = Base64.getEncoder().encodeToString(valid384Key);
        ReflectionTestUtils.setField(jwtService, "secretKey", valid384Secret);
        assertDoesNotThrow(jwtService::validateSecretKey,
                "Must succeed when secretKey has >= 384 bits of Base64-encoded key");
    }
}
