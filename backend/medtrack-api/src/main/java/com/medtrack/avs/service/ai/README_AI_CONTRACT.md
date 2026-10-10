# MedTrack – AVS Phase 20.1: Read-Only AI Operations Assistant Data Contract

## 1. Architectural Scope & Purpose
The MedTrack – AVS AI Operations Assistant is designed strictly as a **read-only conversational interface** providing operational insights on hospital assets, GPS telemetry status, geofence breaches, active transfers, and signature metrics (AMC, HFI, GUB).

This document formalizes the **read-only data contract and security boundaries** governing future phases (Phase 20.2+).

---

## 2. Core Security & Authorization Principles

### 2.1 Server-Side Identity Authority
* **Zero Client Trust:** Never trust any role, user ID, department ID, or permissions specified in user prompt text, HTTP headers, or request payload parameters.
* **Server-Side Authentication:** All operations derive user identity and authorities exclusively from the verified Spring Security context (`SecurityContextHolder`).
* **Role Enforcing:**
  - `STAFF`: Access limited strictly to operational assets and transfers within assigned department.
  - `SUPERVISOR`: Departmental supervision and operational overview across assigned facilities.
  - `ADMIN`: Global hospital console access across all departments.

### 2.2 Prohibited Actions & Capabilities
* **No Database Modification:** The AI layer must NEVER execute INSERT, UPDATE, or DELETE operations.
* **No Workflow Execution:** The AI layer must NEVER trigger asset status changes, approve transfers, modify GPS sessions, edit geofence boundaries, or resolve/delete alerts.
* **No Arbitrary Code/Query Execution:** The model is strictly prohibited from generating or executing arbitrary SQL, JPQL, HQL, shell commands, script engines, or unrestricted HTTP requests.
* **No Dynamic Database Inspection:** The model will never have direct access to database connections, entity managers, JDBC drivers, or schema metadata.

---

## 3. Data Retrieval & Minimization Contract

### 3.1 Allowlisted Retrieval Methods
Data provided to the AI layer must be fetched exclusively through explicit, compile-time allowlisted Spring service and repository methods:
* `AssetService.getAssetById(Long id)` / `getAssetByCode(String code)`
* `TrackingService.getLatestTelemetry(Long assetId)`
* `AlertService.getActiveGeofenceAlerts(Pageable pageable)`
* `TransferService.getActiveTransfers(Pageable pageable)`
* `AnalyticsService.getOverviewMetrics()` (AMC, HFI, GUB)

### 3.2 Data Minimization & Privacy Protection
* **No Bulk Dumps:** Never transmit entire database tables, bulk location history tables, raw GPS telemetry streams, or full user rosters to the AI provider.
* **Secret Redaction:** Passwords, password hashes, JWT secrets, Google OAuth secrets, AI API keys, and session tokens must NEVER be queried, mapped, or included in AI prompt context.
* **PII Minimization:** Exclude staff personal phone numbers, home addresses, or employee private details. Only operational staff names and roles are permitted.
* **Untrusted Content:** All user queries and external inputs are sanitized and treated as untrusted strings to prevent prompt injection attacks.
* **Explicit Uncertainty:** The assistant must explicitly report when data is missing, offline, or unmeasured rather than hallucinating status.

---

## 4. Phase 20.1 Implementation Status
* Server-side provider configuration (`AiProperties`, `AiConfiguration`) with strict connect/read timeouts.
* Safe degradation: Returns HTTP `503 Service Unavailable` with error code `AI_NOT_CONFIGURED` when API key is missing.
* Zero secret leakage: API keys and auth headers are never logged or exposed in JSON responses.
* Fully authenticated endpoint: `POST /api/ai/chat` protected by Spring Security filter chain.
