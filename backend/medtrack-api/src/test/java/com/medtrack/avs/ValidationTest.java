package com.medtrack.avs;

import com.medtrack.avs.dto.AssetCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid AssetCreateRequest should have no constraint violations")
    void testValidAssetRequest() {
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("ECG-023")
                .assetName("CardioScan 500")
                .category("Monitoring Equipment")
                .serialNumber("SN-12345")
                .departmentId(1L)
                .batteryLevel(85)
                .build();


        Set<ConstraintViolation<AssetCreateRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Blank fields and invalid battery level should trigger violations")
    void testInvalidAssetRequest() {
        AssetCreateRequest request = AssetCreateRequest.builder()
                .assetCode("") // blank
                .assetName("") // blank
                .category("")  // blank
                .serialNumber("") // blank
                .batteryLevel(150) // exceeds 100
                .build();

        Set<ConstraintViolation<AssetCreateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.size() >= 5);
    }
}
