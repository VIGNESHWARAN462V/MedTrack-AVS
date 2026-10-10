package com.medtrack.avs.service;

import com.medtrack.avs.dto.analytics.*;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final GpsSessionRepository gpsSessionRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final AssetTransferRepository assetTransferRepository;
    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;

    public static final int TELEMETRY_INTERVAL_SECONDS = 5;
    public static final int GAP_THRESHOLD_SECONDS = 15;
    public static final double HFI_DURATION_WEIGHT = 0.40;
    public static final double HFI_DELAY_WEIGHT = 0.35;
    public static final double HFI_GAP_WEIGHT = 0.25;

    public record AnalyticsDateRange(Instant from, Instant to) {}

    public AnalyticsDateRange resolveDateRange(Instant from, Instant to) {
        Instant effectiveTo = (to != null) ? to.truncatedTo(ChronoUnit.MICROS) : Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant effectiveFrom = (from != null) ? from.truncatedTo(ChronoUnit.MICROS) : effectiveTo.minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("The 'from' timestamp (" + effectiveFrom + ") must be before or equal to 'to' timestamp (" + effectiveTo + ")");
        }
        return new AnalyticsDateRange(effectiveFrom, effectiveTo);
    }

    private void validateAccess(User currentUser, Long departmentId, Long assetId) {
        if (currentUser == null || currentUser.getRole() == null) {
            return;
        }
        if (currentUser.getRole().getName() == RoleType.STAFF) {
            if (departmentId != null) {
                boolean hasAssignmentInDept = assetAssignmentRepository.findByAssignedUserId(currentUser.getId()).stream()
                        .anyMatch(a -> a.getDepartment() != null && a.getDepartment().getId().equals(departmentId));
                if (!hasAssignmentInDept) {
                    throw new AccessDeniedException("STAFF users are not authorized to view analytics for other departments (Department ID: " + departmentId + ")");
                }
            }
            if (assetId != null) {
                boolean isAssignedToAsset = assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(assetId, currentUser.getId());
                if (!isAssignedToAsset) {
                    throw new AccessDeniedException("STAFF users are not authorized to view analytics for asset ID " + assetId + " which is not assigned to them");
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public AmcMetricResponse calculateAmc(Instant from, Instant to, Long departmentId, Long assetId, User currentUser) {
        validateAccess(currentUser, departmentId, assetId);
        AnalyticsDateRange range = resolveDateRange(from, to);

        List<GpsSession> sessions = gpsSessionRepository.findSessionsInPeriod(range.from(), range.to(), departmentId, assetId);
        List<LocationHistory> updates = locationHistoryRepository.findUpdatesInPeriod(range.from(), range.to(), departmentId, assetId);

        long totalActiveDurationSeconds = 0;
        long totalExpectedUpdates = 0;

        for (GpsSession s : sessions) {
            Instant start = s.getStartedAt().isBefore(range.from()) ? range.from() : s.getStartedAt();
            Instant end = s.getEndedAt() != null ? s.getEndedAt() : Instant.now().truncatedTo(ChronoUnit.MICROS);
            if (end.isAfter(range.to())) {
                end = range.to();
            }
            long duration = Math.max(0, Duration.between(start, end).getSeconds());
            totalActiveDurationSeconds += duration;
            long expected = Math.max(1, (long) Math.ceil(duration / (double) TELEMETRY_INTERVAL_SECONDS));
            totalExpectedUpdates += expected;
        }

        long successfulUpdates = updates.size();
        int assetsTracked = updates.stream().map(u -> u.getAsset().getId()).collect(Collectors.toSet()).size();

        Double avgAccuracy = null;
        Double bestAccuracy = null;
        Double worstAccuracy = null;

        DoubleSummaryStatistics accStats = updates.stream()
                .filter(u -> u.getAccuracy() != null)
                .mapToDouble(LocationHistory::getAccuracy)
                .summaryStatistics();

        if (accStats.getCount() > 0) {
            avgAccuracy = Math.round(accStats.getAverage() * 100.0) / 100.0;
            bestAccuracy = Math.round(accStats.getMin() * 100.0) / 100.0;
            worstAccuracy = Math.round(accStats.getMax() * 100.0) / 100.0;
        }

        // Calculate telemetry gaps
        int gapsCount = 0;
        long totalGapDuration = 0;
        Map<Long, List<LocationHistory>> byAsset = updates.stream().collect(Collectors.groupingBy(u -> u.getAsset().getId()));
        for (List<LocationHistory> assetUpdates : byAsset.values()) {
            for (int i = 1; i < assetUpdates.size(); i++) {
                long gap = Duration.between(assetUpdates.get(i - 1).getRecordedAt(), assetUpdates.get(i).getRecordedAt()).getSeconds();
                if (gap > GAP_THRESHOLD_SECONDS) {
                    gapsCount++;
                    totalGapDuration += (gap - TELEMETRY_INTERVAL_SECONDS);
                }
            }
        }

        Double continuityPercentage = null;
        String status;
        String interpretation;

        if (totalExpectedUpdates == 0) {
            status = "NO_DATA";
            interpretation = "No active GPS tracking sessions recorded in selected period";
        } else {
            double raw = ((double) successfulUpdates / totalExpectedUpdates) * 100.0;
            continuityPercentage = Math.min(100.0, Math.round(raw * 100.0) / 100.0);
            status = "MEASURED";
            if (continuityPercentage >= 90.0) {
                interpretation = "High Telemetry Continuity - Equipment signal meets continuous clinical monitoring standard";
            } else if (continuityPercentage >= 70.0) {
                interpretation = "Moderate Telemetry Continuity - Minor telemetry packet loss observed";
            } else {
                interpretation = "Degraded Telemetry Continuity - Frequent tracking dropouts detected";
            }
        }

        // Build time series buckets
        List<AmcDataPointDto> timeSeries = buildAmcTimeSeries(range.from(), range.to(), updates, sessions);

        String deptName = null;
        if (departmentId != null) {
            deptName = departmentRepository.findById(departmentId).map(Department::getName).orElse(null);
        }
        String assetCode = null;
        if (assetId != null) {
            assetCode = assetRepository.findById(assetId).map(Asset::getAssetCode).orElse(null);
        }

        return AmcMetricResponse.builder()
                .metricName("Asset Movement Continuity (AMC)")
                .continuityPercentage(continuityPercentage)
                .successfulGpsUpdates(successfulUpdates)
                .expectedGpsUpdates(totalExpectedUpdates)
                .activeTrackingDurationSeconds(totalActiveDurationSeconds)
                .sessionCount(sessions.size())
                .assetsTrackedCount(assetsTracked)
                .averageAccuracyMeters(avgAccuracy)
                .bestAccuracyMeters(bestAccuracy)
                .worstAccuracyMeters(worstAccuracy)
                .trackingGapsCount(gapsCount)
                .totalGapDurationSeconds(totalGapDuration)
                .telemetryIntervalSeconds(TELEMETRY_INTERVAL_SECONDS)
                .status(status)
                .unit("%")
                .periodStart(range.from())
                .periodEnd(range.to())
                .departmentId(departmentId)
                .departmentName(deptName)
                .assetId(assetId)
                .assetCode(assetCode)
                .interpretation(interpretation)
                .timeSeries(timeSeries)
                .build();
    }

    private List<AmcDataPointDto> buildAmcTimeSeries(Instant from, Instant to, List<LocationHistory> updates, List<GpsSession> sessions) {
        long daysBetween = Math.max(1, Duration.between(from, to).toDays() + 1);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

        Map<String, Long> successByDay = updates.stream()
                .collect(Collectors.groupingBy(u -> dtf.format(u.getRecordedAt()), Collectors.counting()));

        List<AmcDataPointDto> series = new ArrayList<>();
        LocalDate startDay = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDay = to.atZone(ZoneOffset.UTC).toLocalDate();

        for (LocalDate d = startDay; !d.isAfter(endDay); d = d.plusDays(1)) {
            String dayKey = d.toString();
            Instant dayStart = d.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            long expectedDay = 0;
            for (GpsSession s : sessions) {
                Instant sStart = s.getStartedAt().isBefore(dayStart) ? dayStart : s.getStartedAt();
                Instant sEnd = s.getEndedAt() != null ? s.getEndedAt() : Instant.now().truncatedTo(ChronoUnit.MICROS);
                if (sEnd.isAfter(dayEnd)) sEnd = dayEnd;
                if (!sEnd.isBefore(sStart)) {
                    long dur = Duration.between(sStart, sEnd).getSeconds();
                    expectedDay += Math.max(1, (long) Math.ceil(dur / (double) TELEMETRY_INTERVAL_SECONDS));
                }
            }

            long successDay = successByDay.getOrDefault(dayKey, 0L);
            Double pct = expectedDay > 0 ? Math.min(100.0, Math.round(((double) successDay / expectedDay) * 10000.0) / 100.0) : null;

            series.add(AmcDataPointDto.builder()
                    .dateLabel(dayKey)
                    .timestamp(dayStart)
                    .successfulUpdates(successDay)
                    .expectedUpdates(expectedDay)
                    .continuityPercentage(pct)
                    .build());
        }
        return series;
    }

    @Transactional(readOnly = true)
    public HfiMetricResponse calculateHfi(Instant from, Instant to, Long departmentId, Long assetId, User currentUser) {
        validateAccess(currentUser, departmentId, assetId);
        AnalyticsDateRange range = resolveDateRange(from, to);

        List<AssetTransfer> transfers = assetTransferRepository.findCompletedTransfersInPeriod(range.from(), range.to(), departmentId, assetId);

        String deptName = null;
        if (departmentId != null) {
            deptName = departmentRepository.findById(departmentId).map(Department::getName).orElse(null);
        }
        String assetCode = null;
        if (assetId != null) {
            assetCode = assetRepository.findById(assetId).map(Asset::getAssetCode).orElse(null);
        }

        if (transfers.isEmpty()) {
            return HfiMetricResponse.builder()
                    .metricName("Handoff Friction Index (HFI)")
                    .overallHfi(null)
                    .completedTransferCount(0)
                    .averageTransferDurationSeconds(0.0)
                    .averageHandoffDelaySeconds(0.0)
                    .averageTrackingGapRatio(0.0)
                    .durationComponentScore(0.0)
                    .delayComponentScore(0.0)
                    .gapComponentScore(0.0)
                    .durationWeight(HFI_DURATION_WEIGHT)
                    .delayWeight(HFI_DELAY_WEIGHT)
                    .gapWeight(HFI_GAP_WEIGHT)
                    .formula("HFI = (0.40 * S_dur) + (0.35 * S_delay) + (0.25 * S_gap)")
                    .transitReferenceMinutes(60.0)
                    .handoffDelayReferenceMinutes(45.0)
                    .thresholdDocumentation("MedTrack project-configured reference thresholds: Transit Reference = 60 minutes, Acceptance-to-Start Reference = 45 minutes")
                    .status("NO_DATA")
                    .unit("score (0-100)")
                    .periodStart(range.from())
                    .periodEnd(range.to())
                    .departmentId(departmentId)
                    .departmentName(deptName)
                    .assetId(assetId)
                    .assetCode(assetCode)
                    .interpretation("No completed asset transfers found in selected period")
                    .transfers(Collections.emptyList())
                    .departmentBreakdown(Collections.emptyList())
                    .build();
        }

        List<TransferHfiDto> transferHfiList = new ArrayList<>();
        double sumDurationScore = 0.0;
        double sumDelayScore = 0.0;
        double sumGapScore = 0.0;
        double sumTotalHfi = 0.0;
        long sumTransitSeconds = 0;
        long sumDelaySeconds = 0;
        double sumGapRatio = 0.0;

        for (AssetTransfer t : transfers) {
            Instant start = t.getStartedAt() != null ? t.getStartedAt() : t.getAcceptedAt();
            if (start == null) start = t.getRequestedAt();
            Instant end = t.getCompletedAt();

            long transitSeconds = Math.max(0, Duration.between(start, end).getSeconds());
            double durationScore = Math.min(100.0, (transitSeconds / 3600.0) * 100.0);

            long delaySeconds = (t.getAcceptedAt() != null && t.getStartedAt() != null)
                    ? Math.max(0, Duration.between(t.getAcceptedAt(), t.getStartedAt()).getSeconds())
                    : 0;
            double delayScore = Math.min(100.0, (delaySeconds / 2700.0) * 100.0);

            List<LocationHistory> transitLocations = locationHistoryRepository
                    .findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc(t.getAsset().getId(), start, end);

            long gapDurationSeconds = 0;
            if (transitLocations.isEmpty()) {
                gapDurationSeconds = transitSeconds;
            } else {
                long leadGap = Duration.between(start, transitLocations.get(0).getRecordedAt()).getSeconds();
                if (leadGap > GAP_THRESHOLD_SECONDS) {
                    gapDurationSeconds += (leadGap - TELEMETRY_INTERVAL_SECONDS);
                }
                for (int i = 1; i < transitLocations.size(); i++) {
                    long gap = Duration.between(transitLocations.get(i - 1).getRecordedAt(), transitLocations.get(i).getRecordedAt()).getSeconds();
                    if (gap > GAP_THRESHOLD_SECONDS) {
                        gapDurationSeconds += (gap - TELEMETRY_INTERVAL_SECONDS);
                    }
                }
                long trailGap = Duration.between(transitLocations.get(transitLocations.size() - 1).getRecordedAt(), end).getSeconds();
                if (trailGap > GAP_THRESHOLD_SECONDS) {
                    gapDurationSeconds += (trailGap - TELEMETRY_INTERVAL_SECONDS);
                }
            }

            double gapRatio = transitSeconds > 0 ? Math.min(1.0, (double) gapDurationSeconds / transitSeconds) : 0.0;
            double gapScore = Math.min(100.0, gapRatio * 100.0);

            double hfiScore = Math.round((HFI_DURATION_WEIGHT * durationScore +
                                          HFI_DELAY_WEIGHT * delayScore +
                                          HFI_GAP_WEIGHT * gapScore) * 100.0) / 100.0;

            sumDurationScore += durationScore;
            sumDelayScore += delayScore;
            sumGapScore += gapScore;
            sumTotalHfi += hfiScore;
            sumTransitSeconds += transitSeconds;
            sumDelaySeconds += delaySeconds;
            sumGapRatio += gapRatio;

            transferHfiList.add(TransferHfiDto.builder()
                    .transferId(t.getId())
                    .assetId(t.getAsset().getId())
                    .assetCode(t.getAsset().getAssetCode())
                    .assetName(t.getAsset().getAssetName())
                    .sourceDepartmentId(t.getFromDepartment() != null ? t.getFromDepartment().getId() : null)
                    .sourceDepartmentName(t.getFromDepartment() != null ? t.getFromDepartment().getName() : null)
                    .destinationDepartmentId(t.getToDepartment() != null ? t.getToDepartment().getId() : null)
                    .destinationDepartmentName(t.getToDepartment() != null ? t.getToDepartment().getName() : null)
                    .transferDurationSeconds(transitSeconds)
                    .handoffDelaySeconds(delaySeconds)
                    .trackingGapDurationSeconds(gapDurationSeconds)
                    .trackingGapRatio(Math.round(gapRatio * 1000.0) / 1000.0)
                    .durationScore(Math.round(durationScore * 100.0) / 100.0)
                    .delayScore(Math.round(delayScore * 100.0) / 100.0)
                    .gapScore(Math.round(gapScore * 100.0) / 100.0)
                    .hfiScore(hfiScore)
                    .requestedAt(t.getRequestedAt())
                    .acceptedAt(t.getAcceptedAt())
                    .startedAt(t.getStartedAt())
                    .completedAt(t.getCompletedAt())
                    .build());
        }

        int count = transfers.size();
        double overallHfi = Math.round((sumTotalHfi / count) * 100.0) / 100.0;
        double avgDur = Math.round(((double) sumTransitSeconds / count) * 10.0) / 10.0;
        double avgDelay = Math.round(((double) sumDelaySeconds / count) * 10.0) / 10.0;
        double avgGapRatio = Math.round((sumGapRatio / count) * 1000.0) / 1000.0;

        // Department breakdown grouped by destination department
        Map<Long, List<TransferHfiDto>> byDestDept = transferHfiList.stream()
                .filter(t -> t.getDestinationDepartmentId() != null)
                .collect(Collectors.groupingBy(TransferHfiDto::getDestinationDepartmentId));

        List<DepartmentHfiDto> deptBreakdown = new ArrayList<>();
        for (Map.Entry<Long, List<TransferHfiDto>> entry : byDestDept.entrySet()) {
            List<TransferHfiDto> dList = entry.getValue();
            String dName = dList.get(0).getDestinationDepartmentName();
            double dHfi = Math.round(dList.stream().mapToDouble(TransferHfiDto::getHfiScore).average().orElse(0.0) * 100.0) / 100.0;
            double dDur = Math.round(dList.stream().mapToDouble(TransferHfiDto::getTransferDurationSeconds).average().orElse(0.0) * 10.0) / 10.0;
            double dDelay = Math.round(dList.stream().mapToDouble(TransferHfiDto::getHandoffDelaySeconds).average().orElse(0.0) * 10.0) / 10.0;

            deptBreakdown.add(DepartmentHfiDto.builder()
                    .departmentId(entry.getKey())
                    .departmentName(dName)
                    .transferCount(dList.size())
                    .averageHfi(dHfi)
                    .averageDurationSeconds(dDur)
                    .averageDelaySeconds(dDelay)
                    .build());
        }

        String interpretation;
        if (overallHfi <= 25.0) {
            interpretation = "Low Friction - Rapid physical handoff with continuous telemetry and minimal dispatch delay";
        } else if (overallHfi <= 60.0) {
            interpretation = "Moderate Friction - Acceptable turnaround with mild dispatch delays or tracking dropouts";
        } else {
            interpretation = "High Friction - Significant operational bottlenecks in custody acceptance or physical transport";
        }

        return HfiMetricResponse.builder()
                .metricName("Handoff Friction Index (HFI)")
                .overallHfi(overallHfi)
                .completedTransferCount(count)
                .averageTransferDurationSeconds(avgDur)
                .averageHandoffDelaySeconds(avgDelay)
                .averageTrackingGapRatio(avgGapRatio)
                .durationComponentScore(Math.round((sumDurationScore / count) * 100.0) / 100.0)
                .delayComponentScore(Math.round((sumDelayScore / count) * 100.0) / 100.0)
                .gapComponentScore(Math.round((sumGapScore / count) * 100.0) / 100.0)
                .durationWeight(HFI_DURATION_WEIGHT)
                .delayWeight(HFI_DELAY_WEIGHT)
                .gapWeight(HFI_GAP_WEIGHT)
                .formula("HFI = (0.40 * S_dur) + (0.35 * S_delay) + (0.25 * S_gap)")
                .transitReferenceMinutes(60.0)
                .handoffDelayReferenceMinutes(45.0)
                .thresholdDocumentation("MedTrack project-configured reference thresholds: Transit Reference = 60 minutes, Acceptance-to-Start Reference = 45 minutes")
                .status("MEASURED")
                .unit("score (0-100)")
                .periodStart(range.from())
                .periodEnd(range.to())
                .departmentId(departmentId)
                .departmentName(deptName)
                .assetId(assetId)
                .assetCode(assetCode)
                .interpretation(interpretation)
                .transfers(transferHfiList)
                .departmentBreakdown(deptBreakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public GubMetricResponse calculateGub(Instant from, Instant to, Long departmentId, User currentUser) {
        validateAccess(currentUser, departmentId, null);
        AnalyticsDateRange range = resolveDateRange(from, to);

        List<Department> departments;
        if (departmentId != null) {
            departments = departmentRepository.findById(departmentId).map(List::of).orElse(Collections.emptyList());
        } else {
            departments = departmentRepository.findAll();
        }

        List<DepartmentGubDto> deptList = new ArrayList<>();
        long totalUsable = 0;
        long totalDemand = 0;
        double sumAbsDeviation = 0.0;
        double sumBalance = 0.0;

        for (Department d : departments) {
            List<Asset> assets = assetRepository.findByDepartmentId(d.getId());

            long usable = assets.stream().filter(a ->
                    a.getStatus() == AssetStatus.AVAILABLE ||
                    a.getStatus() == AssetStatus.ASSIGNED ||
                    a.getStatus() == AssetStatus.IN_USE).count();

            long available = assets.stream().filter(a -> a.getStatus() == AssetStatus.AVAILABLE).count();
            long assigned = assets.stream().filter(a -> a.getStatus() == AssetStatus.ASSIGNED).count();
            long inUse = assets.stream().filter(a -> a.getStatus() == AssetStatus.IN_USE).count();
            long maint = assets.stream().filter(a -> a.getStatus() == AssetStatus.MAINTENANCE || a.getStatus() == AssetStatus.OFFLINE).count();

            long inboundTransfers = assetTransferRepository.countInboundTransfersInPeriod(d.getId(), range.from(), range.to());
            long outboundTransfers = assetTransferRepository.countOutboundTransfersInPeriod(d.getId(), range.from(), range.to());

            // Demand is modeled deterministically from inbound transfer requests + active clinical assignments
            long demand = inboundTransfers + assigned;

            double balanceScore;
            if (usable == 0 && demand == 0) {
                balanceScore = 0.0;
            } else {
                balanceScore = Math.round(((double) (usable - demand) / (usable + demand)) * 10000.0) / 100.0;
            }

            String status;
            if (balanceScore > 10.0) {
                status = "SURPLUS";
            } else if (balanceScore < -10.0) {
                status = "DEFICIT";
            } else {
                status = "BALANCED";
            }

            totalUsable += usable;
            totalDemand += demand;
            sumAbsDeviation += Math.abs(balanceScore);
            sumBalance += balanceScore;

            deptList.add(DepartmentGubDto.builder()
                    .departmentId(d.getId())
                    .departmentName(d.getName())
                    .departmentCode(d.getCode())
                    .usableAssetCount(usable)
                    .availableAssetCount(available)
                    .assignedAssetCount(assigned)
                    .inUseAssetCount(inUse)
                    .maintenanceAssetCount(maint)
                    .inboundTransferCount(inboundTransfers)
                    .outboundTransferCount(outboundTransfers)
                    .demandCount(demand)
                    .balanceScore(balanceScore)
                    .status(status)
                    .build());
        }

        int deptCount = departments.size();
        Double hospitalEquilibrium = null;
        Double avgBalance = null;

        if (deptCount > 0) {
            double meanDeviation = sumAbsDeviation / deptCount;
            hospitalEquilibrium = Math.max(0.0, Math.round((100.0 - meanDeviation) * 100.0) / 100.0);
            avgBalance = Math.round((sumBalance / deptCount) * 100.0) / 100.0;
        }

        String interpretation;
        if (hospitalEquilibrium != null && hospitalEquilibrium >= 80.0) {
            interpretation = "High Hospital Equilibrium - Equipment allocation is well-balanced across acute wards";
        } else if (hospitalEquilibrium != null && hospitalEquilibrium >= 50.0) {
            interpretation = "Moderate Equilibrium - Localized supply imbalances observed between acute and general departments";
        } else {
            interpretation = "Uneven Allocation - Significant device clustering or deficit in specific departments";
        }

        return GubMetricResponse.builder()
                .metricName("Geo-Utilization Balance (GUB)")
                .hospitalEquilibriumScore(hospitalEquilibrium)
                .averageBalanceScore(avgBalance)
                .totalUsableAssets(totalUsable)
                .totalDemand(totalDemand)
                .departmentCount(deptCount)
                .supplyDefinition("Assets with status AVAILABLE, ASSIGNED, or IN_USE located in department")
                .demandDefinition("Inbound transfer requests + active clinical assignments in department")
                .formula("GUB = ((Supply - Demand) / (Supply + Demand)) * 100")
                .status(deptCount > 0 ? "MEASURED" : "NO_DATA")
                .unit("index (-100 to +100)")
                .periodStart(range.from())
                .periodEnd(range.to())
                .interpretation(interpretation)
                .departments(deptList)
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse calculateOverview(Instant from, Instant to, Long departmentId, Long assetId, User currentUser) {
        AmcMetricResponse amc = calculateAmc(from, to, departmentId, assetId, currentUser);
        HfiMetricResponse hfi = calculateHfi(from, to, departmentId, assetId, currentUser);
        GubMetricResponse gub = calculateGub(from, to, departmentId, currentUser);

        String deptName = null;
        if (departmentId != null) {
            deptName = departmentRepository.findById(departmentId).map(Department::getName).orElse(null);
        }
        String assetCode = null;
        if (assetId != null) {
            assetCode = assetRepository.findById(assetId).map(Asset::getAssetCode).orElse(null);
        }

        return AnalyticsOverviewResponse.builder()
                .periodStart(amc.getPeriodStart())
                .periodEnd(amc.getPeriodEnd())
                .departmentId(departmentId)
                .departmentName(deptName)
                .assetId(assetId)
                .assetCode(assetCode)
                .amcPercentage(amc.getContinuityPercentage())
                .amcStatus(amc.getStatus())
                .successfulGpsUpdates(amc.getSuccessfulGpsUpdates())
                .expectedGpsUpdates(amc.getExpectedGpsUpdates())
                .trackedAssetsCount(amc.getAssetsTrackedCount())
                .hfiScore(hfi.getOverallHfi())
                .hfiStatus(hfi.getStatus())
                .completedTransfersCount(hfi.getCompletedTransferCount())
                .averageTransferDurationSeconds(hfi.getAverageTransferDurationSeconds())
                .averageHandoffDelaySeconds(hfi.getAverageHandoffDelaySeconds())
                .hospitalEquilibriumScore(gub.getHospitalEquilibriumScore())
                .gubStatus(gub.getStatus())
                .totalUsableAssets(gub.getTotalUsableAssets())
                .totalDemand(gub.getTotalDemand())
                .amcTimeSeries(amc.getTimeSeries())
                .departmentBalances(gub.getDepartments())
                .recentTransfers(hfi.getTransfers())
                .build();
    }
}
