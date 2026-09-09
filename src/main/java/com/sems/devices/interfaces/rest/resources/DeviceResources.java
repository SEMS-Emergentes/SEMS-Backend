package com.sems.devices.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sems.devices.domain.model.aggregates.Device;
import com.sems.devices.domain.model.entities.DeviceBinding;
import com.sems.devices.domain.model.entities.DeviceConfiguration;
import com.sems.devices.domain.model.entities.DeviceEvent;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Contrato JSON del modulo de dispositivos.
 *
 * <p>Los nombres de campo replican exactamente los del servicio en Go, incluido
 * el uso de camelCase y la omision de los opcionales vacios. El frontend no
 * distingue si detras hay Go o Java.
 */
public final class DeviceResources {

    private DeviceResources() {
    }

    // ------------------------------------------------------------- peticiones

    public record CreateDeviceRequest(
            @NotBlank(message = "is required") String externalDeviceCode,
            @NotBlank(message = "is required") String userId,
            // El local es obligatorio: el medidor, el contrato y la factura son
            // suyos, no de la persona que da de alta el equipo.
            @NotBlank(message = "is required") String siteId,
            // La zona no: al abrir un local puede que aun no esten definidas.
            String zoneId,
            @NotBlank(message = "is required") String deviceName,
            @NotBlank(message = "is required") String deviceType,
            String brand,
            String model,
            @NotBlank(message = "is required") String connectionProtocol) {
    }

    public record UpdateDeviceRequest(
            @NotBlank(message = "is required") String deviceName,
            @NotBlank(message = "is required") String deviceType,
            String brand,
            String model,
            @NotBlank(message = "is required") String connectionProtocol,
            // Un equipo se traslada de zona; de local no, que seria otro suministro.
            String zoneId) {
    }

    public record UpdateDeviceStatusRequest(
            @NotBlank(message = "is required") String status) {
    }

    public record CreateBindingRequest(
            @NotBlank(message = "is required") String userId,
            String siteId) {
    }

    public record CreateConfigurationRequest(
            @NotBlank(message = "is required") String configKey,
            String configValue) {
    }

    public record UpdateConfigurationRequest(String configValue) {
    }

    public record CreateEventRequest(
            @NotBlank(message = "is required") String eventType,
            String description,
            Instant occurredAt) {
    }

    // -------------------------------------------------------------- respuestas

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeviceResource(
            String deviceId, String externalDeviceCode, String userId, String siteId, String zoneId,
            String deviceName, String deviceType, String brand, String model,
            String connectionProtocol, String status, Instant registeredAt, Instant updatedAt) {

        public static DeviceResource from(Device d) {
            return new DeviceResource(
                    d.getDeviceId().toString(), d.getExternalDeviceCode(), d.getUserId().toString(),
                    d.getSiteId().toString(),
                    d.getZoneId() == null ? null : d.getZoneId().toString(),
                    d.getDeviceName(), d.getDeviceType(), d.getBrand(), d.getModel(),
                    d.getConnectionProtocol().name(), d.getStatus().name(),
                    d.getRegisteredAt(), d.getUpdatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeviceBindingResource(
            String bindingId, String deviceId, String userId, String siteId,
            String bindingStatus, Instant linkedAt, Instant unlinkedAt, Instant updatedAt) {

        public static DeviceBindingResource from(DeviceBinding b) {
            return new DeviceBindingResource(
                    b.getBindingId().toString(), b.getDeviceId().toString(), b.getUserId().toString(),
                    b.getSiteId() == null ? null : b.getSiteId().toString(),
                    b.getBindingStatus().name(), b.getLinkedAt(), b.getUnlinkedAt(), b.getUpdatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeviceConfigurationResource(
            String configurationId, String deviceId, String configKey,
            String configValue, Instant updatedAt) {

        public static DeviceConfigurationResource from(DeviceConfiguration c) {
            return new DeviceConfigurationResource(
                    c.getConfigurationId().toString(), c.getDeviceId().toString(),
                    c.getConfigKey(), c.getConfigValue(), c.getUpdatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeviceEventResource(
            String eventId, String deviceId, String eventType,
            String description, Instant occurredAt) {

        public static DeviceEventResource from(DeviceEvent e) {
            return new DeviceEventResource(
                    e.getEventId().toString(), e.getDeviceId().toString(),
                    e.getEventType(), e.getDescription(), e.getOccurredAt());
        }
    }
}
