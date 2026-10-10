package com.medtrack.avs.service.ai;

import com.medtrack.avs.service.ai.model.AiIntent;
import com.medtrack.avs.service.ai.model.AiIntentClassification;
import com.medtrack.avs.service.ai.model.AiToolName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constrained question router and intent classifier for the AI Operations Assistant (Phase 20.2).
 * Strictly maps user queries to allowlisted tools, detects mutation attempts, and enforces prompt injection boundaries.
 */
@Component
@Slf4j
public class AiQuestionRouter {

    private static final Pattern ASSET_CODE_PATTERN = Pattern.compile("\\b([A-Za-z]{2,5}-\\d{3,4})\\b");

    private static final Pattern ACTION_REQUEST_PATTERN = Pattern.compile(
            "\\b(delete|remove|destroy|truncate|drop\\s+table|create\\s+(asset|user|geofence|session|transfer)|add\\s+(asset|user|geofence)|insert\\s+(into|asset|record)|update\\s+(asset|department|status|location|user|record|[A-Za-z]{2,5}-\\d{3,4})|modify\\s+(asset|department|status|user)|change\\s+status|change\\s+department|edit\\s+(asset|department|status|user)|set\\s+|" +
            "start\\s+(gps|tracking|session)|begin\\s+tracking|stop\\s+(gps|tracking|session)|end\\s+session|" +
            "record\\s+location|send\\s+telemetry|resolve\\s+alert|clear\\s+alert|dismiss\\s+alert|" +
            "accept\\s+transfer|reject\\s+transfer|cancel\\s+transfer|complete\\s+transfer|request\\s+transfer|approve\\s+(the\\s+)?(pending\\s+)?transfer|" +
            "assign\\s+asset|reassign\\s+asset|change\\s+role|promote\\s+user|grant\\s+admin)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "\\b(ignore\\s+(all\\s+)?(previous|prior|system)\\s+instructions|system\\s+prompt|system\\s+instructions|" +
            "reveal\\s+(the\\s+)?(api\\s*key|api-key|secret|password|jwt|token)|tell\\s+me\\s+(the\\s+)?(api\\s*key|secret|password)|" +
            "bypass\\s+(role|permission|auth|security|access)|elevate\\s+(to\\s+)?(admin|administrator|privileges?)|escalate\\s+privileges|" +
            "(use|assume|grant|gain|act\\s+as)\\s+(an?\\s+)?(admin|administrator|root)\\s+(access|role|privileges?|permissions?)|" +
            "use\\s+administrator\\s+access|" +
            "dump\\s+(database|db|users)|select\\s+\\*\\s+from|exec\\s*\\(|(run|execute)?\\s*sql\\b|list\\s+(all|every)\\s+users?|dump\\s+(users?|tables?)|database\\s+query)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "\\b(where|location|coordinates|lat|lng|latitude|longitude|last\\s+seen|position)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STALE_TRACKING_PATTERN = Pattern.compile(
            "\\b(stale[a-z]*|oldest|outdated|inactive|freshness|no\\s+recent\\s+updates?|no\\s+updates?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ALERTS_PATTERN = Pattern.compile(
            "\\b(alerts?|geofence|violations?|unresolved)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRANSFERS_PATTERN = Pattern.compile(
            "\\b(transfers?|in\\s+transit|in\\s+progress|handoff|pending\\s+transfer)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AMC_PATTERN = Pattern.compile(
            "\\b(amc|asset\\s+movement\\s+continuity|continuity)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HFI_PATTERN = Pattern.compile(
            "\\b(hfi|handoff\\s+friction(\\s+index)?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GUB_PATTERN = Pattern.compile(
            "\\b(gub|geo-utilization\\s+balance|utilization\\s+balance|deficit|surplus|hospital\\s+equilibrium|balance\\s+score)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Classifies a user question into a strict operational intent and extracts parameters.
     */
    public AiIntentClassification classify(String question) {
        if (question == null || question.trim().isEmpty()) {
            return AiIntentClassification.builder()
                    .intent(AiIntent.UNSUPPORTED)
                    .requiresDirectRefusal(true)
                    .directResponse("Please provide an operational question regarding MedTrack assets, locations, alerts, transfers, or analytics.")
                    .build();
        }

        String trimmed = question.trim();

        // 1. Check for prompt injection / security overrides
        if (PROMPT_INJECTION_PATTERN.matcher(trimmed).find()) {
            log.warn("[AI Security] Detected prompt injection attempt in question: '{}'", trimmed);
            return AiIntentClassification.builder()
                    .intent(AiIntent.PROMPT_INJECTION)
                    .requiresDirectRefusal(true)
                    .directResponse("I am the MedTrack AI Operations Assistant. I can only answer authorized operational questions about hospital assets, alerts, transfers, and analytics based on your server-side permissions.")
                    .build();
        }

        // 2. Check for action / mutation requests
        if (ACTION_REQUEST_PATTERN.matcher(trimmed).find()) {
            log.info("[AI Router] Detected action/mutation request in question: '{}'", trimmed);
            return AiIntentClassification.builder()
                    .intent(AiIntent.ACTION_REQUEST)
                    .requiresDirectRefusal(true)
                    .directResponse("The MedTrack AI Operations Assistant is strictly read-only and cannot create, modify, or delete assets, tracking sessions, transfers, or alerts. Please use the MedTrack web dashboard or mobile scanner to perform authorized operational actions.")
                    .build();
        }

        // 3. Extract entities
        String assetCode = extractAssetCode(trimmed);
        String departmentNameOrCode = extractDepartment(trimmed);
        String metricName = extractMetric(trimmed);

        List<AiToolName> targetTools = new ArrayList<>();

        // 4. Determine operational intent
        // Case A: Metric Questions (AMC, HFI, GUB)
        if (metricName != null) {
            if ("GUB".equalsIgnoreCase(metricName) && (trimmed.toLowerCase().contains("deficit") || trimmed.toLowerCase().contains("surplus") || trimmed.toLowerCase().contains("department"))) {
                targetTools.add(AiToolName.GET_DEPARTMENT_BALANCE);
                return AiIntentClassification.builder()
                        .intent(AiIntent.GET_DEPARTMENT_BALANCE)
                        .targetTools(targetTools)
                        .metricName("GUB")
                        .departmentNameOrCode(departmentNameOrCode)
                        .assetCode(assetCode)
                        .build();
            } else {
                targetTools.add(AiToolName.GET_ANALYTICS_METRIC);
                return AiIntentClassification.builder()
                        .intent(AiIntent.GET_ANALYTICS_METRIC)
                        .targetTools(targetTools)
                        .metricName(metricName.toUpperCase())
                        .departmentNameOrCode(departmentNameOrCode)
                        .assetCode(assetCode)
                        .build();
            }
        }

        // Case B: Department balance / deficit / surplus without explicit GUB keyword
        if (GUB_PATTERN.matcher(trimmed).find()) {
            targetTools.add(AiToolName.GET_DEPARTMENT_BALANCE);
            return AiIntentClassification.builder()
                    .intent(AiIntent.GET_DEPARTMENT_BALANCE)
                    .targetTools(targetTools)
                    .metricName("GUB")
                    .departmentNameOrCode(departmentNameOrCode)
                    .build();
        }

        // Case C: Stale tracking / GPS staleness across assets
        if (STALE_TRACKING_PATTERN.matcher(trimmed).find()) {
            targetTools.add(AiToolName.GET_TRACKING_STATUS);
            if (assetCode != null) {
                targetTools.add(AiToolName.LOOKUP_ASSET);
            }
            return AiIntentClassification.builder()
                    .intent(AiIntent.GET_TRACKING_STATUS)
                    .targetTools(targetTools)
                    .assetCode(assetCode)
                    .staleOnly(true)
                    .build();
        }

        // Case D: Asset Location inquiry
        if (assetCode != null && (LOCATION_PATTERN.matcher(trimmed).find() || trimmed.toLowerCase().contains("seen") || trimmed.toLowerCase().contains("report"))) {
            targetTools.add(AiToolName.GET_LATEST_LOCATION);
            targetTools.add(AiToolName.LOOKUP_ASSET);
            return AiIntentClassification.builder()
                    .intent(AiIntent.GET_LATEST_LOCATION)
                    .targetTools(targetTools)
                    .assetCode(assetCode)
                    .build();
        }

        // Case E: Alerts inquiry
        if (ALERTS_PATTERN.matcher(trimmed).find()) {
            targetTools.add(AiToolName.GET_RECENT_ALERTS);
            return AiIntentClassification.builder()
                    .intent(AiIntent.GET_RECENT_ALERTS)
                    .targetTools(targetTools)
                    .assetCode(assetCode)
                    .resolved(trimmed.toLowerCase().contains("unresolved") ? Boolean.FALSE : null)
                    .limit(10)
                    .build();
        }

        // Case F: Transfers inquiry
        if (TRANSFERS_PATTERN.matcher(trimmed).find()) {
            targetTools.add(AiToolName.GET_ACTIVE_TRANSFERS);
            return AiIntentClassification.builder()
                    .intent(AiIntent.GET_ACTIVE_TRANSFERS)
                    .targetTools(targetTools)
                    .assetCode(assetCode)
                    .departmentNameOrCode(departmentNameOrCode)
                    .limit(10)
                    .build();
        }

        // Case G: Specific Asset Lookup (e.g. "Tell me about ECG-001" or "Status of ECG-001")
        if (assetCode != null) {
            targetTools.add(AiToolName.LOOKUP_ASSET);
            return AiIntentClassification.builder()
                    .intent(AiIntent.LOOKUP_ASSET)
                    .targetTools(targetTools)
                    .assetCode(assetCode)
                    .build();
        }

        // Case H: General / Unsupported
        log.info("[AI Router] Unrecognized or unsupported inquiry: '{}'", trimmed);
        return AiIntentClassification.builder()
                .intent(AiIntent.UNSUPPORTED)
                .requiresDirectRefusal(true)
                .directResponse("I can answer operational questions regarding hospital assets, GPS locations, tracking status, geofence alerts, asset transfers, and analytics (AMC, HFI, GUB). Please specify what operational information you need.")
                .build();
    }

    private String extractAssetCode(String text) {
        Matcher matcher = ASSET_CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    private String extractDepartment(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("cardiology") || lower.contains("card")) return "Cardiology";
        if (lower.contains("emergency") || lower.contains("er")) return "Emergency";
        if (lower.contains("intensive care") || lower.contains("icu")) return "Intensive Care Unit";
        if (lower.contains("radiology") || lower.contains("rad")) return "Radiology";
        if (lower.contains("general ward") || lower.contains("gw")) return "General Ward";
        return null;
    }

    private String extractMetric(String text) {
        if (AMC_PATTERN.matcher(text).find()) return "AMC";
        if (HFI_PATTERN.matcher(text).find()) return "HFI";
        if (GUB_PATTERN.matcher(text).find()) return "GUB";
        return null;
    }
}
