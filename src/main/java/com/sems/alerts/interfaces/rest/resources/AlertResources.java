package com.sems.alerts.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sems.alerts.domain.model.entities.*;
import com.sems.alerts.domain.model.valueobjects.Operator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Contrato JSON del modulo de alertas, en snake_case como el original. */
public final class AlertResources {

    private AlertResources() {
    }

    // ------------------------------------------------------------- peticiones

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateAlertRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String deviceId,
            String thresholdId,
            String inactivityRuleId,
            @NotBlank(message = "is required") String alertType,
            @NotBlank(message = "is required") String title,
            @NotBlank(message = "is required") String message,
            @NotBlank(message = "is required") String severity,
            String status,
            Instant triggeredAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateAlertStatusRequest(
            @NotBlank(message = "is required") String status,
            Instant resolvedAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateThresholdRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String deviceId,
            @NotBlank(message = "is required") String thresholdName,
            @NotBlank(message = "is required") String metric,
            @NotBlank(message = "is required") String operator,
            double thresholdValue,
            Boolean active) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateInactivityRuleRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String deviceId,
            @NotBlank(message = "is required") String ruleName,
            int maxInactiveMinutes,
            boolean active) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreatePreferenceRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String channel,
            boolean enabled,
            String minSeverity,
            Instant quietHoursStart,
            Instant quietHoursEnd) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateDemandRuleRequest(
            @NotBlank(message = "is required") String siteId,
            @NotBlank(message = "is required") String userId,
            String ruleName,
            // Opcional: la pone el local. Si viene y no coincide, se rechaza.
            Double contractedPowerKw,
            Double warningPercent,
            Boolean active) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EvaluateDemandRequest(
            @NotNull(message = "is required") Double demandKw) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DemandRuleResponse(String demandRuleId, String siteId, String userId,
                                     String ruleName, double contractedPowerKw,
                                     double warningPercent, double warningThresholdKw,
                                     boolean active, Instant createdAt) {

        public static DemandRuleResponse from(DemandRule r) {
            return new DemandRuleResponse(r.getDemandRuleId().toString(), r.getSiteId().toString(),
                    r.getUserId().toString(), r.getRuleName(), r.getContractedPowerKw(),
                    r.getWarningPercent(),
                    Math.round(r.umbralDeAvisoKw() * 100.0) / 100.0, r.isActive(),
                    r.getCreatedAt());
        }
    }

    // -------------------------------------------------------------- respuestas

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AlertResponse(
            String alertId, String userId, String deviceId, String thresholdId,
            String inactivityRuleId, String alertType, String title, String message,
            String severity, String status, Instant triggeredAt, Instant resolvedAt) {

        public static AlertResponse from(Alert a) {
            return new AlertResponse(a.getAlertId().toString(), a.getUserId().toString(),
                    a.getDeviceId() == null ? null : a.getDeviceId().toString(),
                    a.getThresholdId() == null ? null : a.getThresholdId().toString(),
                    a.getInactivityRuleId() == null ? null : a.getInactivityRuleId().toString(),
                    a.getAlertType(), a.getTitle(), a.getMessage(), a.getSeverity(),
                    a.getStatus(), a.getTriggeredAt(), a.getResolvedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ThresholdResponse(
            String thresholdId, String userId, String deviceId, String thresholdName,
            String metric, Operator operator, double thresholdValue, boolean active) {

        public static ThresholdResponse from(AlertThreshold t) {
            return new ThresholdResponse(t.getThresholdId().toString(), t.getUserId().toString(),
                    t.getDeviceId() == null ? null : t.getDeviceId().toString(),
                    t.getThresholdName(), t.getMetric(), t.getOperator(), t.getThresholdValue(),
                    t.isActive());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InactivityRuleResponse(
            String inactivityRuleId, String userId, String deviceId, String ruleName,
            int maxInactiveMinutes, boolean active) {

        public static InactivityRuleResponse from(InactivityRule r) {
            return new InactivityRuleResponse(r.getInactivityRuleId().toString(),
                    r.getUserId().toString(),
                    r.getDeviceId() == null ? null : r.getDeviceId().toString(),
                    r.getRuleName(), r.getMaxInactiveMinutes(), r.isActive());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PreferenceResponse(
            String preferenceId, String userId, String channel, boolean enabled,
            String minSeverity, Instant quietHoursStart, Instant quietHoursEnd) {

        public static PreferenceResponse from(NotificationPreference p) {
            return new PreferenceResponse(p.getPreferenceId().toString(), p.getUserId().toString(),
                    p.getChannel(), p.isEnabled(), p.getMinSeverity(), p.getQuietHoursStart(),
                    p.getQuietHoursEnd());
        }
    }
}
