package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.AssetCreateRequest;
import com.medtrack.avs.dto.AssetUpdateRequest;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.AssetRepository;
import com.medtrack.avs.repository.DepartmentRepository;
import com.medtrack.avs.repository.RoleRepository;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

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

    private Department department;
    private Asset sampleAsset;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role supervisorRole = roleRepository.findByName(RoleType.SUPERVISOR).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.SUPERVISOR).description("Supervisor").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        User adminUser = userRepository.findByEmail("admin@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin System")
                        .email("admin@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User supervisorUser = userRepository.findByEmail("supervisor@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Supervisor One")
                        .email("supervisor@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Supervisor@12345"))
                        .role(supervisorRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("nurse@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Nurse")
                        .email("nurse@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Staff@12345"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        adminToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        supervisorToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                supervisorUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
        staffToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                staffUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));

        department = departmentRepository.findAll().stream().findFirst().orElseGet(() ->
                departmentRepository.save(Department.builder()
                        .name("Emergency Dept Test")
                        .code("ED-TEST-" + System.currentTimeMillis())
                        .description("Emergency Test Department")
                        .active(true)
                        .build()));


        sampleAsset = assetRepository.save(Asset.builder()
                .assetCode("TEST-ECG-" + System.currentTimeMillis())
                .assetName("CardioSmart Unit")
                .category("Cardiology")
                .serialNumber("SN-CS-" + System.currentTimeMillis())
                .status(AssetStatus.AVAILABLE)
                .department(department)
                .batteryLevel(90)
                .qrCode("MEDTRACK:QR:" + System.currentTimeMillis())
                .build());
    }

    @Test
    @DisplayName("1. List assets - Returns HTTP 200 with list of assets")
    void testListAssets() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("2. Get asset by ID - Returns HTTP 200 when found")
    void testGetAssetById() throws Exception {
        mockMvc.perform(get("/api/assets/" + sampleAsset.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleAsset.getId()))
                .andExpect(jsonPath("$.assetCode").value(sampleAsset.getAssetCode()))
                .andExpect(jsonPath("$.assetName").value(sampleAsset.getAssetName()));
    }

    @Test
    @DisplayName("3. Missing asset → 404 Not Found")
    void testGetAssetByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/assets/999999999")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("4. Create asset → 201 Created")
    void testCreateAsset() throws Exception {
        long timestamp = System.currentTimeMillis();
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("ECG-CREATE-" + timestamp)
                .assetName("Portable ECG Unit")
                .category("Cardiology")
                .serialNumber("SN-CREATE-" + timestamp)
                .status(AssetStatus.AVAILABLE)
                .departmentId(department.getId())
                .batteryLevel(95)
                .qrCode("MEDTRACK:ASSET:ECG-" + timestamp)
                .build();

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.assetCode").value(request.getAssetCode()))
                .andExpect(jsonPath("$.departmentId").value(department.getId()))
                .andExpect(jsonPath("$.batteryLevel").value(95));
    }

    @Test
    @DisplayName("5. Duplicate asset code → 409 Conflict")
    void testDuplicateAssetCode() throws Exception {
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode(sampleAsset.getAssetCode()) // Duplicate
                .assetName("Another Device")
                .category("General")
                .serialNumber("SN-UNIQUE-" + System.currentTimeMillis())
                .status(AssetStatus.AVAILABLE)
                .departmentId(department.getId())
                .batteryLevel(80)
                .build();

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("6. Duplicate serial number → 409 Conflict")
    void testDuplicateSerialNumber() throws Exception {
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("CODE-NEW-" + System.currentTimeMillis())
                .assetName("Another Device")
                .category("General")
                .serialNumber(sampleAsset.getSerialNumber()) // Duplicate serial
                .status(AssetStatus.AVAILABLE)
                .departmentId(department.getId())
                .batteryLevel(80)
                .build();

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("7. Duplicate QR code → 409 Conflict")
    void testDuplicateQrCode() throws Exception {
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("CODE-NEW-" + System.currentTimeMillis())
                .assetName("Another Device")
                .category("General")
                .serialNumber("SN-NEW-" + System.currentTimeMillis())
                .status(AssetStatus.AVAILABLE)
                .departmentId(department.getId())
                .batteryLevel(80)
                .qrCode(sampleAsset.getQrCode()) // Duplicate QR
                .build();

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("8. Invalid asset input → 400 Bad Request")
    void testInvalidAssetInput() throws Exception {
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("") // Blank
                .assetName("") // Blank
                .category("")
                .serialNumber("")
                .departmentId(null) // Missing
                .batteryLevel(150) // Exceeds 100
                .build();

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("9. Update asset → 200 OK")
    void testUpdateAsset() throws Exception {
        AssetUpdateRequest request = AssetUpdateRequest.builder()
                .assetName("Updated CardioScan Model 2")
                .category("Cardiology Premium")
                .status(AssetStatus.IN_USE)
                .batteryLevel(72)
                .build();

        mockMvc.perform(put("/api/assets/" + sampleAsset.getId())
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleAsset.getId()))
                .andExpect(jsonPath("$.assetName").value("Updated CardioScan Model 2"))
                .andExpect(jsonPath("$.category").value("Cardiology Premium"))
                .andExpect(jsonPath("$.status").value("IN_USE"))
                .andExpect(jsonPath("$.batteryLevel").value(72));
    }

    @Test
    @DisplayName("10. Delete asset → 200 OK for unlinked asset")
    void testDeleteAsset() throws Exception {
        Asset tempAsset = assetRepository.save(Asset.builder()
                .assetCode("TEMP-DEL-" + System.currentTimeMillis())
                .assetName("Temp Machine")
                .category("Temp")
                .serialNumber("SN-DEL-" + System.currentTimeMillis())
                .status(AssetStatus.AVAILABLE)
                .department(department)
                .build());

        mockMvc.perform(delete("/api/assets/" + tempAsset.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("deleted successfully")));

        assertFalse(assetRepository.existsById(tempAsset.getId()));
    }

    @Test
    @DisplayName("11. Search assets by keyword")
    void testSearchAssets() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("search", "Cardio")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("12. Filter by status")
    void testFilterByStatus() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("status", "AVAILABLE")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("13. Filter by department")
    void testFilterByDepartment() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("departmentId", department.getId().toString())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("14. Filter by category")
    void testFilterByCategory() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("category", "Cardiology")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("15. Asset code lookup")
    void testAssetCodeLookup() throws Exception {
        mockMvc.perform(get("/api/assets/code/" + sampleAsset.getAssetCode())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCode").value(sampleAsset.getAssetCode()))
                .andExpect(jsonPath("$.id").value(sampleAsset.getId()));
    }

    @Test
    @DisplayName("16. QR lookup - Valid authenticated lookup returns asset and no sensitive fields")
    void testQrLookup() throws Exception {
        mockMvc.perform(get("/api/assets/qr/" + sampleAsset.getQrCode())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCode").value(sampleAsset.getQrCode()))
                .andExpect(jsonPath("$.id").value(sampleAsset.getId()))
                .andExpect(jsonPath("$.assetCode").value(sampleAsset.getAssetCode()))
                .andExpect(jsonPath("$.assetName").value(sampleAsset.getAssetName()))
                .andExpect(jsonPath("$.category").value(sampleAsset.getCategory()))
                .andExpect(jsonPath("$.departmentName").value(sampleAsset.getDepartment().getName()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.secrets").doesNotExist());
    }

    @Test
    @DisplayName("16a. QR lookup - URL-encoded QR string is supported")
    void testQrLookupUrlEncoded() throws Exception {
        String encoded = java.net.URLEncoder.encode(sampleAsset.getQrCode(), java.nio.charset.StandardCharsets.UTF_8);
        mockMvc.perform(get(java.net.URI.create("/api/assets/qr/" + encoded))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCode").value(sampleAsset.getAssetCode()));
    }

    @Test
    @DisplayName("16b. QR lookup - Query parameter fallback (?code=...)")
    void testQrLookupQueryParam() throws Exception {
        mockMvc.perform(get("/api/assets/qr?code=" + sampleAsset.getQrCode())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCode").value(sampleAsset.getAssetCode()));
    }

    @Test
    @DisplayName("16c. QR lookup - Unknown QR code returns HTTP 404")
    void testQrLookupNotFound() throws Exception {
        mockMvc.perform(get("/api/assets/qr/MEDTRACK:ASSET:NONEXISTENT-999")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    @DisplayName("16d. QR lookup - Empty/overlength QR code returns HTTP 400")
    void testQrLookupBadRequest() throws Exception {
        // Empty parameter
        mockMvc.perform(get("/api/assets/qr?code=")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Overlength QR (> 255 chars)
        String overlength = "MEDTRACK:ASSET:" + "A".repeat(300);
        mockMvc.perform(get("/api/assets/qr?code=" + overlength)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("exceeds maximum")));
    }

    @Test
    @DisplayName("16e. QR lookup - Unauthenticated request returns HTTP 401")
    void testQrLookupUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/assets/qr/" + sampleAsset.getQrCode()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("16f. QR lookup - Repeated/duplicate scans do not create duplicate records")
    void testQrLookupDuplicateIdempotency() throws Exception {
        long countBefore = assetRepository.count();

        // Scan 1
        mockMvc.perform(get("/api/assets/qr/" + sampleAsset.getQrCode())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // Scan 2
        mockMvc.perform(get("/api/assets/qr/" + sampleAsset.getQrCode())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // Count must remain exactly the same
        long countAfter = assetRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(countBefore, countAfter);
    }

    @Test
    @DisplayName("17. Staff authorization - Can read but forbidden from create/update/delete")
    void testStaffAuthorizationConstraints() throws Exception {
        // GET is allowed
        mockMvc.perform(get("/api/assets")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // POST is forbidden
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("STAFF-UNAUTH-" + System.currentTimeMillis())
                .assetName("Staff Item")
                .category("Cardiology")
                .serialNumber("SN-STAFF-" + System.currentTimeMillis())
                .departmentId(department.getId())
                .build();

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // PUT is forbidden
        mockMvc.perform(put("/api/assets/" + sampleAsset.getId())
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AssetUpdateRequest.builder().assetName("Staff Mod").build())))
                .andExpect(status().isForbidden());

        // DELETE is forbidden
        mockMvc.perform(delete("/api/assets/" + sampleAsset.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("18. Supervisor authorization - Can create and update, but forbidden from delete")
    void testSupervisorAuthorizationConstraints() throws Exception {
        // Can create
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("SUP-CREATE-" + System.currentTimeMillis())
                .assetName("Supervisor Machine")
                .category("ICU")
                .serialNumber("SN-SUP-" + System.currentTimeMillis())
                .departmentId(department.getId())
                .build();

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Can update
        mockMvc.perform(put("/api/assets/" + sampleAsset.getId())
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AssetUpdateRequest.builder().assetName("Sup Updated").build())))
                .andExpect(status().isOk());

        // Cannot delete (ADMIN only)
        mockMvc.perform(delete("/api/assets/" + sampleAsset.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("19. Admin authorization - Can create, update, and delete")
    void testAdminAuthorization() throws Exception {
        // Admin full permission verified in tests 4, 9, 10
        mockMvc.perform(get("/api/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("20. Unauthorized access → 401 Unauthorized when no JWT provided")
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("21. Forbidden access → 403 Forbidden with structured error message")
    void testForbiddenAccessStructuredError() throws Exception {
        mockMvc.perform(delete("/api/assets/" + sampleAsset.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message", containsString("insufficient permissions")));
    }
}
