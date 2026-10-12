package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FloorPlanImageUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private FloorPlanImageRepository floorPlanImageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String supervisorToken;
    private String staffToken;

    private Floor testFloor;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role supervisorRole = roleRepository.findByName(RoleType.SUPERVISOR).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.SUPERVISOR).description("Supervisor").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        User adminUser = userRepository.findByEmail("admin-upload-test@medtrack.local").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin Uploader")
                        .email("admin-upload-test@medtrack.local")
                        .passwordHash(passwordEncoder.encode("SecretAdmin123!"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User supervisorUser = userRepository.findByEmail("supervisor-upload-test@medtrack.local").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Supervisor Member")
                        .email("supervisor-upload-test@medtrack.local")
                        .passwordHash(passwordEncoder.encode("SecretSup123!"))
                        .role(supervisorRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("staff-upload-test@medtrack.local").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Member")
                        .email("staff-upload-test@medtrack.local")
                        .passwordHash(passwordEncoder.encode("SecretStaff123!"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        adminToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        adminUser.getEmail(), adminUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        supervisorToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        supervisorUser.getEmail(), supervisorUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
                )
        );

        staffToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        staffUser.getEmail(), staffUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
                )
        );

        Hospital hospital = hospitalRepository.save(Hospital.builder()
                .code("UPLOAD-HOSP")
                .name("Upload Test Hospital")
                .address("100 CAD Way")
                .latitude(40.7128)
                .longitude(-74.0060)
                .geofenceRadiusMeters(200.0)
                .active(true)
                .build());

        Building building = buildingRepository.save(Building.builder()
                .hospital(hospital)
                .code("UP-BLD")
                .name("Upload Pavilion")
                .latitude(40.7129)
                .longitude(-74.0061)
                .active(true)
                .build());

        testFloor = floorRepository.save(Floor.builder()
                .building(building)
                .floorNumber(1)
                .displayName("Upload Level 1")
                .floorPlanWidth(1000)
                .floorPlanHeight(650)
                .active(true)
                .build());
    }

    private byte[] createValidPngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLUE);
        g2d.drawRect(10, 10, width - 20, height - 20);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private byte[] createValidJpegBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Admin can upload a valid PNG floor plan image and receive updated dimensions")
    void testUploadValidPngFloorPlan() throws Exception {
        byte[] pngBytes = createValidPngBytes(1200, 800);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "floor-1-blueprint.png",
                "image/png",
                pngBytes
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.floorId").value(testFloor.getId()))
                .andExpect(jsonPath("$.originalFileName").value("floor-1-blueprint.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.width").value(1200))
                .andExpect(jsonPath("$.height").value(800))
                .andExpect(jsonPath("$.imageUrl").value("/api/floors/" + testFloor.getId() + "/floor-plan-image"))
                .andExpect(jsonPath("$.fileSize").value(pngBytes.length));

        Floor updated = floorRepository.findById(testFloor.getId()).orElseThrow();
        assertEquals("/api/floors/" + testFloor.getId() + "/floor-plan-image", updated.getFloorPlanImageUrl());
        assertEquals(1200, updated.getFloorPlanWidth());
        assertEquals(800, updated.getFloorPlanHeight());
    }

    @Test
    @DisplayName("Admin can upload a valid JPEG architectural blueprint")
    void testUploadValidJpegFloorPlan() throws Exception {
        byte[] jpegBytes = createValidJpegBytes(1024, 768);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "floor-blueprint.jpg",
                "image/jpeg",
                jpegBytes
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(1024))
                .andExpect(jsonPath("$.height").value(768));
    }

    @Test
    @DisplayName("Upload strictly rejects SVG uploads with a clear validation message")
    void testUploadRejectsSvgFormatWithClearMessage() throws Exception {
        String safeSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1400 900\">" +
                "<rect width=\"1400\" height=\"900\" fill=\"#f8fafc\"/>" +
                "</svg>";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "architectural-plan.svg",
                "image/svg+xml",
                safeSvg.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SVG uploads are not permitted for security reasons")));
    }

    @Test
    @DisplayName("Upload rejects malicious SVG variations containing script tags")
    void testUploadRejectsSvgWithScript() throws Exception {
        String unsafeSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert('XSS')</script></svg>";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "xss.svg",
                "image/svg+xml",
                unsafeSvg.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SVG uploads are not permitted")));
    }

    @Test
    @DisplayName("Upload rejects malicious SVG variations containing XXE")
    void testUploadRejectsSvgWithXxe() throws Exception {
        String xxeSvg = "<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><svg><text>&xxe;</text></svg>";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "xxe.svg",
                "image/svg+xml",
                xxeSvg.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SVG uploads are not permitted")));
    }

    @Test
    @DisplayName("Upload rejects unauthenticated request with 401 Unauthorized")
    void testUploadRejectsUnauthenticated() throws Exception {
        byte[] pngBytes = createValidPngBytes(800, 600);
        MockMultipartFile file = new MockMultipartFile("file", "plan.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Upload rejects non-admin STAFF user with 403 Forbidden")
    void testUploadRejectsStaff() throws Exception {
        byte[] pngBytes = createValidPngBytes(800, 600);
        MockMultipartFile file = new MockMultipartFile("file", "plan.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Upload rejects SUPERVISOR user with 403 Forbidden (Admin-only mutation)")
    void testUploadRejectsSupervisor() throws Exception {
        byte[] pngBytes = createValidPngBytes(800, 600);
        MockMultipartFile file = new MockMultipartFile("file", "plan.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Upload rejects unsupported executable and document formats")
    void testUploadRejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/octet-stream",
                "MZexecutableDummyBytesContentHere".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported image format")));
    }

    @Test
    @DisplayName("Upload rejects file with fake PNG extension but invalid magic bytes")
    void testUploadRejectsSpoofedMagicBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "This is actually a plain text file pretending to be PNG".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("File header does not match valid PNG magic bytes")));
    }

    @Test
    @DisplayName("Upload rejects spoofed MIME types disguised with PNG extension")
    void testUploadRejectsSpoofedMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sneaky.png",
                "text/html",
                "<html><body>malicious payload</body></html>".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET floor-plan-image requires authentication: Unauthenticated returns 401 Unauthorized")
    void testGetFloorPlanImageRequiresAuth_UnauthenticatedReturns401() throws Exception {
        byte[] pngBytes = createValidPngBytes(600, 400);
        MockMultipartFile file = new MockMultipartFile("file", "protected-plan.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        // Unauthenticated GET must return 401
        mockMvc.perform(get("/api/floors/" + testFloor.getId() + "/floor-plan-image"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET floor-plan-image permits authenticated STAFF, SUPERVISOR, and ADMIN with security headers")
    void testGetFloorPlanImage_AuthenticatedRolesPermittedWithSecurityHeaders() throws Exception {
        byte[] pngBytes = createValidPngBytes(600, 400);
        MockMultipartFile file = new MockMultipartFile("file", "hospital-blueprint.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        // 1. Staff access
        mockMvc.perform(get("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(pngBytes));

        // 2. Supervisor access
        mockMvc.perform(get("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pngBytes));

        // 3. Admin access
        mockMvc.perform(get("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pngBytes));
    }

    @Test
    @DisplayName("GET floor-plan-image returns 404 Not Found for nonexistent floor or unconfigured image")
    void testGetFloorPlanImageNotFound() throws Exception {
        // Floor without image
        mockMvc.perform(get("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Nonexistent floor
        mockMvc.perform(get("/api/floors/999999/floor-plan-image")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Admin can delete uploaded floor plan image and reset floor metadata cleanly")
    void testDeleteFloorPlanImage() throws Exception {
        byte[] pngBytes = createValidPngBytes(600, 400);
        MockMultipartFile file = new MockMultipartFile("file", "to-delete.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        assertTrue(floorPlanImageRepository.existsByFloorId(testFloor.getId()));

        // Non-admin cannot delete
        mockMvc.perform(delete("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        // Delete as Admin
        mockMvc.perform(delete("/api/floors/" + testFloor.getId() + "/floor-plan-image")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(floorPlanImageRepository.existsByFloorId(testFloor.getId()));
        Floor refreshed = floorRepository.findById(testFloor.getId()).orElseThrow();
        assertNull(refreshed.getFloorPlanImageUrl());
    }
}
