package com.medtrack.avs.config;

import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final GeofenceRepository geofenceRepository;
    private final HospitalRepository hospitalRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${medtrack.dev.admin-password:${MEDTRACK_DEV_ADMIN_PASSWORD:}}")
    private String devAdminPassword;

    @Value("${medtrack.dev.supervisor-password:${MEDTRACK_DEV_SUPERVISOR_PASSWORD:}}")
    private String devSupervisorPassword;

    @Value("${medtrack.dev.staff-password:${MEDTRACK_DEV_STAFF_PASSWORD:}}")
    private String devStaffPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting MedTrack - AVS Phase 10 Data Layer Seeding...");

        seedRoles();
        seedDepartments();
        seedHospitals();
        seedHospitalHierarchy();
        seedUsers();
        seedAssets();
        seedAssetAssignments();
        seedGeofences();
        seedIndoorAssetPositions();

        log.info("MedTrack - AVS Phase 10 Data Layer Seeding completed successfully.");
    }

    private void seedRoles() {
        createRoleIfNotFound(RoleType.ADMIN, "System Administrator with full access");
        createRoleIfNotFound(RoleType.SUPERVISOR, "Department Supervisor with asset & transfer management");
        createRoleIfNotFound(RoleType.STAFF, "Hospital Clinical and Logistics Staff");
    }

    private void createRoleIfNotFound(RoleType name, String description) {
        if (!roleRepository.existsByName(name)) {
            Role role = Role.builder()
                    .name(name)
                    .description(description)
                    .build();
            roleRepository.save(role);
            log.info("Seeded Role: {}", name);
        } else {
            log.debug("Role already exists: {}", name);
        }
    }

    private void seedDepartments() {
        createDepartmentIfNotFound("ER", "Emergency", "Emergency Department & Trauma Center", true);
        createDepartmentIfNotFound("ICU", "Intensive Care Unit", "Intensive Care & Critical Care Unit", true);
        createDepartmentIfNotFound("CARD", "Cardiology", "Cardiovascular Medicine & Catheterization Labs", true);
        createDepartmentIfNotFound("RAD", "Radiology", "Diagnostic Imaging, MRI & CT Scanning", true);
        createDepartmentIfNotFound("GW", "General Ward", "Inpatient General Medical & Surgical Wards", true);
    }

    private void createDepartmentIfNotFound(String code, String name, String description, boolean active) {
        if (!departmentRepository.existsByCode(code)) {
            Department department = Department.builder()
                    .code(code)
                    .name(name)
                    .description(description)
                    .active(active)
                    .build();
            departmentRepository.save(department);
            log.info("Seeded Department: {} ({})", name, code);
        } else {
            log.debug("Department already exists: {}", code);
        }
    }

    private void seedUsers() {
        seedUserAccount("admin@medtrack.local", "MedTrack Admin", RoleType.ADMIN, devAdminPassword);
        seedUserAccount("supervisor@medtrack.local", "Clinical Supervisor", RoleType.SUPERVISOR, devSupervisorPassword);
        seedUserAccount("staff@medtrack.local", "Care Unit Staff", RoleType.STAFF, devStaffPassword);
    }

    private void seedUserAccount(String email, String name, RoleType roleType, String configuredPassword) {
        String effectivePassword = (configuredPassword != null && !configuredPassword.trim().isEmpty())
                ? configuredPassword.trim()
                : UUID.randomUUID().toString();

        if (!userRepository.existsByEmail(email)) {
            Role role = roleRepository.findByName(roleType)
                    .orElseThrow(() -> new IllegalStateException("Role not found during user seeding: " + roleType));

            User user = User.builder()
                    .email(email)
                    .name(name)
                    .passwordHash(passwordEncoder.encode(effectivePassword))
                    .authProvider(AuthProvider.LOCAL)
                    .enabled(true)
                    .role(role)
                    .build();
            userRepository.save(user);
            log.info("Seeded User: {} with Role: {}", email, roleType);
        } else {
            log.debug("User already exists: {}", email);
            if (configuredPassword != null && !configuredPassword.trim().isEmpty()) {
                userRepository.findByEmail(email).ifPresent(user -> {
                    user.setPasswordHash(passwordEncoder.encode(configuredPassword.trim()));
                    userRepository.save(user);
                    log.info("Updated password for configured user: {}", email);
                });
            }
        }
    }

    private void seedAssets() {
        Department er = departmentRepository.findByCode("ER").orElse(null);
        Department icu = departmentRepository.findByCode("ICU").orElse(null);
        Department card = departmentRepository.findByCode("CARD").orElse(null);
        Department rad = departmentRepository.findByCode("RAD").orElse(null);
        Department gw = departmentRepository.findByCode("GW").orElse(null);

        createAssetIfNotFound("ECG-001", "Portable ECG Machine", "Cardiology", "SN-ECG-1001", AssetStatus.ASSIGNED, card, 92);
        createAssetIfNotFound("ECG-002", "Portable ECG Machine", "Emergency", "SN-ECG-1002", AssetStatus.AVAILABLE, er, 85);
        createAssetIfNotFound("PUMP-001", "Infusion Pump", "Infusion System", "SN-PUMP-2001", AssetStatus.ASSIGNED, icu, 78);
        createAssetIfNotFound("PUMP-002", "Infusion Pump", "Infusion System", "SN-PUMP-2002", AssetStatus.AVAILABLE, icu, 95);
        createAssetIfNotFound("MON-001", "Patient Monitor", "Monitoring", "SN-MON-3001", AssetStatus.ASSIGNED, er, 88);
        createAssetIfNotFound("MON-002", "Patient Monitor", "Monitoring", "SN-MON-3002", AssetStatus.AVAILABLE, card, 90);
        createAssetIfNotFound("DEF-001", "Defibrillator", "Resuscitation", "SN-DEF-4001", AssetStatus.AVAILABLE, er, 100);
        createAssetIfNotFound("VENT-001", "Portable Ventilator", "Respiratory", "SN-VENT-5001", AssetStatus.IN_USE, icu, 72);
        createAssetIfNotFound("OXY-001", "Portable Oxygen Cylinder", "Respiratory", "SN-OXY-6001", AssetStatus.AVAILABLE, gw, 100);
        createAssetIfNotFound("OXY-002", "Portable Oxygen Cylinder", "Respiratory", "SN-OXY-6002", AssetStatus.IN_USE, er, 65);
        createAssetIfNotFound("WHL-001", "Wheelchair", "Mobility", "SN-WHL-7001", AssetStatus.AVAILABLE, gw, null);
        createAssetIfNotFound("USG-001", "Portable Ultrasound", "Diagnostic", "SN-USG-8001", AssetStatus.MAINTENANCE, rad, 45);
        createAssetIfNotFound("DIAL-001", "Portable Hemodialysis Machine", "Renal Care", "SN-DIAL-9001", AssetStatus.AVAILABLE, icu, 80);
    }

    private void createAssetIfNotFound(String assetCode, String assetName, String category, String serialNumber,
                                       AssetStatus status, Department department, Integer batteryLevel) {
        if (!assetRepository.existsByAssetCode(assetCode)) {
            Asset asset = Asset.builder()
                    .assetCode(assetCode)
                    .assetName(assetName)
                    .category(category)
                    .serialNumber(serialNumber)
                    .status(status)
                    .department(department)
                    .qrCode("MEDTRACK:ASSET:" + assetCode)
                    .batteryLevel(batteryLevel)
                    .latitude(null)
                    .longitude(null)
                    .gpsAccuracy(null)
                    .lastSeen(null)
                    .build();
            assetRepository.save(asset);
            log.info("Seeded Asset: {} ({}) in Department: {}", assetName, assetCode, department != null ? department.getCode() : "None");
        } else {
            log.debug("Asset already exists: {}", assetCode);
        }
    }

    private void seedAssetAssignments() {
        createAssignmentIfNotFound("ECG-001", "staff@medtrack.local", "CARD");
        createAssignmentIfNotFound("PUMP-001", "staff@medtrack.local", "ICU");
        createAssignmentIfNotFound("MON-001", "supervisor@medtrack.local", "ER");
    }

    private void createAssignmentIfNotFound(String assetCode, String userEmail, String departmentCode) {
        Asset asset = assetRepository.findByAssetCode(assetCode).orElse(null);
        User user = userRepository.findByEmail(userEmail).orElse(null);
        Department department = departmentRepository.findByCode(departmentCode).orElse(null);

        if (asset != null && user != null) {
            if (!assetAssignmentRepository.existsByAssetIdAndActiveTrue(asset.getId())) {
                AssetAssignment assignment = AssetAssignment.builder()
                        .asset(asset)
                        .assignedUser(user)
                        .department(department)
                        .assignedAt(LocalDateTime.now().minusHours(4))
                        .active(true)
                        .build();
                assetAssignmentRepository.save(assignment);
                log.info("Seeded Asset Assignment: Asset {} -> User {} ({})", assetCode, userEmail, departmentCode);
            } else {
                log.debug("Active assignment already exists for asset: {}", assetCode);
            }
        }
    }

    private void seedGeofences() {
        createGeofenceIfNotFound("Cardiology Geofence", "CARD", 11.496155, 77.278545, 50.0);
        createGeofenceIfNotFound("Emergency Department Geofence", "ER", 11.497000, 77.279000, 40.0);
        createGeofenceIfNotFound("ICU Geofence", "ICU", 11.495500, 77.278000, 45.0);
    }

    private void createGeofenceIfNotFound(String name, String departmentCode, double centerLat, double centerLon, double radiusMeters) {
        if (!geofenceRepository.existsByName(name)) {
            Department department = departmentRepository.findByCode(departmentCode).orElse(null);
            Geofence geofence = Geofence.builder()
                    .name(name)
                    .department(department)
                    .centerLatitude(centerLat)
                    .centerLongitude(centerLon)
                    .radiusMeters(radiusMeters)
                    .active(true)
                    .build();
            geofenceRepository.save(geofence);
            log.info("Seeded Geofence: {} (Dept: {}, Center: {}, {}, Radius: {}m)",
                    name, departmentCode, centerLat, centerLon, radiusMeters);
        } else {
            log.debug("Geofence already exists: {}", name);
        }
    }

    private void seedHospitals() {
        if (!hospitalRepository.existsByCode("AVS-MAIN")) {
            Hospital hospital = Hospital.builder()
                    .name("Apex Valley Central Hospital")
                    .code("AVS-MAIN")
                    .address("100 Hospital Way, Medical District")
                    .latitude(11.496155)
                    .longitude(77.278545)
                    .geofenceRadiusMeters(150.0)
                    .active(true)
                    .build();
            hospitalRepository.save(hospital);
            log.info("Seeded Hospital: {} (Code: {}, Coords: [11.496155, 77.278545])",
                    hospital.getName(), hospital.getCode());
        } else {
            log.debug("Hospital already exists: AVS-MAIN");
        }
    }

    private void seedHospitalHierarchy() {
        Hospital hospital = hospitalRepository.findByCode("AVS-MAIN").orElse(null);
        if (hospital == null) {
            log.warn("Cannot seed facility hierarchy: Hospital AVS-MAIN not found.");
            return;
        }

        // 1. Seed Buildings
        Building erBuilding = buildingRepository.findByHospitalIdAndCode(hospital.getId(), "BLD-ER-ACUTE")
                .orElseGet(() -> {
                    Building b = Building.builder()
                            .hospital(hospital)
                            .name("Emergency & Acute Care Block")
                            .code("BLD-ER-ACUTE")
                            .description("Emergency Trauma Center, Critical Care & Inpatient Wards")
                            .latitude(11.496500)
                            .longitude(77.278800)
                            .active(true)
                            .build();
                    Building saved = buildingRepository.save(b);
                    log.info("Seeded Building: {} (Code: {})", saved.getName(), saved.getCode());
                    return saved;
                });

        Building cardBuilding = buildingRepository.findByHospitalIdAndCode(hospital.getId(), "BLD-CARD-PAV")
                .orElseGet(() -> {
                    Building b = Building.builder()
                            .hospital(hospital)
                            .name("Cardiology Pavilion")
                            .code("BLD-CARD-PAV")
                            .description("Cardiovascular Medicine & Diagnostic Catheterization Suites")
                            .latitude(11.495800)
                            .longitude(77.278300)
                            .active(true)
                            .build();
                    Building saved = buildingRepository.save(b);
                    log.info("Seeded Building: {} (Code: {})", saved.getName(), saved.getCode());
                    return saved;
                });

        // 2. Seed Floors
        Floor erGround = floorRepository.findByBuildingIdAndFloorNumber(erBuilding.getId(), 0)
                .orElseGet(() -> {
                    Floor f = Floor.builder()
                            .building(erBuilding)
                            .floorNumber(0)
                            .displayName("Ground Floor")
                            .active(true)
                            .build();
                    Floor saved = floorRepository.save(f);
                    log.info("Seeded Floor: {} for Building {}", saved.getDisplayName(), erBuilding.getName());
                    return saved;
                });

        Floor erFloor1 = floorRepository.findByBuildingIdAndFloorNumber(erBuilding.getId(), 1)
                .orElseGet(() -> {
                    Floor f = Floor.builder()
                            .building(erBuilding)
                            .floorNumber(1)
                            .displayName("Floor 1")
                            .active(true)
                            .build();
                    Floor saved = floorRepository.save(f);
                    log.info("Seeded Floor: {} for Building {}", saved.getDisplayName(), erBuilding.getName());
                    return saved;
                });

        Floor erFloor2 = floorRepository.findByBuildingIdAndFloorNumber(erBuilding.getId(), 2)
                .orElseGet(() -> {
                    Floor f = Floor.builder()
                            .building(erBuilding)
                            .floorNumber(2)
                            .displayName("Floor 2")
                            .active(true)
                            .build();
                    Floor saved = floorRepository.save(f);
                    log.info("Seeded Floor: {} for Building {}", saved.getDisplayName(), erBuilding.getName());
                    return saved;
                });

        Floor cardGround = floorRepository.findByBuildingIdAndFloorNumber(cardBuilding.getId(), 0)
                .orElseGet(() -> {
                    Floor f = Floor.builder()
                            .building(cardBuilding)
                            .floorNumber(0)
                            .displayName("Ground Floor")
                            .active(true)
                            .build();
                    Floor saved = floorRepository.save(f);
                    log.info("Seeded Floor: {} for Building {}", saved.getDisplayName(), cardBuilding.getName());
                    return saved;
                });

        // 3. Seed Rooms
        Department erDept = departmentRepository.findByCode("ER").orElse(null);
        Department icuDept = departmentRepository.findByCode("ICU").orElse(null);
        Department cardDept = departmentRepository.findByCode("CARD").orElse(null);

        createRoomIfNotFound(erGround, erDept, "ER-BAY-01", "Emergency Trauma Bay",
                "Rapid triage, resuscitation & acute trauma monitoring", 5.0, 10.0, 35.0, 30.0);

        createRoomIfNotFound(erFloor1, icuDept, "ICU-ROOM-101", "Intensive Care Unit",
                "High-acuity intensive telemetry and ventilator beds", 45.0, 10.0, 45.0, 35.0);

        createRoomIfNotFound(cardGround, cardDept, "CARD-CLINIC-01", "Cardiology Diagnostic Clinic",
                "ECG, echocardiogram and catheterization consultation", 10.0, 50.0, 40.0, 35.0);

        createRoomIfNotFound(erGround, null, "STOR-CENTRAL-01", "Central Clinical Equipment Storage",
                "Secured reserve for infusion pumps and portable diagnostics", 55.0, 55.0, 35.0, 30.0);
    }

    private void createRoomIfNotFound(Floor floor, Department department, String roomNumber,
                                      String name, String description,
                                      double x, double y, double width, double height) {
        if (!roomRepository.existsByFloorIdAndRoomNumber(floor.getId(), roomNumber)) {
            Room room = Room.builder()
                    .floor(floor)
                    .department(department)
                    .roomNumber(roomNumber)
                    .name(name)
                    .description(description)
                    .xPosition(x)
                    .yPosition(y)
                    .width(width)
                    .height(height)
                    .active(true)
                    .build();
            roomRepository.save(room);
            log.info("Seeded Room: {} ({}) on Floor: {}", name, roomNumber, floor.getDisplayName());
        } else {
            log.debug("Room already exists: {} on Floor: {}", roomNumber, floor.getDisplayName());
        }
    }

    private void seedIndoorAssetPositions() {
        roomRepository.findFirstByRoomNumber("ER-BAY-01").ifPresent(room -> {
            assignIndoorPositionIfUnset("ECG-002", room, 20.0, 25.0);
        });

        roomRepository.findFirstByRoomNumber("ICU-ROOM-101").ifPresent(room -> {
            assignIndoorPositionIfUnset("PUMP-001", room, 65.0, 25.0);
        });

        roomRepository.findFirstByRoomNumber("CARD-CLINIC-01").ifPresent(room -> {
            assignIndoorPositionIfUnset("MON-002", room, 30.0, 65.0);
        });

        roomRepository.findFirstByRoomNumber("STOR-CENTRAL-01").ifPresent(room -> {
            assignIndoorPositionIfUnset("DEF-001", room, 70.0, 70.0);
        });
    }

    private void assignIndoorPositionIfUnset(String assetCode, Room room, double x, double y) {
        assetRepository.findByAssetCode(assetCode).ifPresent(asset -> {
            if (asset.getFloor() == null) {
                Floor floor = room.getFloor();
                Building building = floor.getBuilding();
                Hospital hospital = building.getHospital();

                asset.setHospital(hospital);
                asset.setBuilding(building);
                asset.setFloor(floor);
                asset.setRoom(room);
                asset.setIndoorX(x);
                asset.setIndoorY(y);
                asset.setIndoorLocationSource(IndoorLocationSource.MANUAL_CONFIG);
                asset.setIndoorUpdatedAt(java.time.Instant.now());
                assetRepository.save(asset);
                log.info("Seeded indoor location for Asset {} ({}, {}) in Room {}",
                        assetCode, x, y, room.getRoomNumber());
            }
        });
    }
}
