package com.sems.energy.domain.model.entities;

import com.sems.energy.domain.model.valueobjects.MeterStatus;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Medidor inteligente instalado en un local, y opcionalmente en una zona.
 *
 * <p>Representa el dispositivo fisico EOS que mide el consumo y produce las
 * lecturas. Es dominio puro: sin JPA y sin dependencias de framework.
 *
 * <p>El medidor colgaba solo de un usuario y guardaba su ubicacion como texto
 * libre. Para un supermercado eso no alcanza: un local tiene decenas de
 * medidores repartidos entre camaras de frio, sala de ventas y climatizacion, y
 * atribuir el consumo al area correcta es justamente lo que el cliente compra.
 * El local es obligatorio porque la tarifa, la potencia contratada y el recibo
 * son del suministro, no de la persona que da de alta el equipo. La zona es
 * opcional: al abrir un local puede que aun no esten definidas.
 *
 * <p>El local no cambia; la zona si. Un medidor se traslada de un area a otra
 * del mismo local, pero mudarlo a otro establecimiento seria otro suministro y
 * por tanto otro medidor.
 */
@Getter
public class EnergyMeter {

    private final UUID id;
    private String userId;
    private final String siteId;
    private String zoneId;
    private final String meterSerial;
    private final String model;
    private final String brand;
    private final String location;
    private MeterStatus status;
    private final String firmwareVersion;
    private final double maxPowerWatts;
    private final Instant registeredAt;
    private Instant lastSeenAt;
    private Instant updatedAt;
    private Instant unlinkedAt;

    private EnergyMeter(UUID id, String userId, String siteId, String zoneId, String meterSerial,
                        String model, String brand, String location, MeterStatus status,
                        String firmwareVersion, double maxPowerWatts, Instant registeredAt,
                        Instant lastSeenAt, Instant updatedAt, Instant unlinkedAt) {
        this.id = id;
        this.userId = userId;
        this.siteId = siteId;
        this.zoneId = zoneId;
        this.meterSerial = meterSerial;
        this.model = model;
        this.brand = brand;
        this.location = location;
        this.status = status;
        this.firmwareVersion = firmwareVersion;
        this.maxPowerWatts = maxPowerWatts;
        this.registeredAt = registeredAt;
        this.lastSeenAt = lastSeenAt;
        this.updatedAt = updatedAt;
        this.unlinkedAt = unlinkedAt;
    }

    public static EnergyMeter register(String userId, String siteId, String zoneId,
                                       String meterSerial, String model, String brand,
                                       String location, String firmwareVersion,
                                       Double maxPowerWatts) {
        if (isBlank(userId)) {
            throw AppException.validation("user_id is required");
        }
        if (isBlank(siteId)) {
            throw AppException.validation("site_id is required");
        }
        if (isBlank(meterSerial)) {
            throw AppException.validation("meter_serial is required");
        }
        Instant now = Instant.now();
        return new EnergyMeter(UUID.randomUUID(), userId.trim(), siteId.trim(), limpiar(zoneId),
                meterSerial.trim(), model, brand, location, MeterStatus.ACTIVE,
                firmwareVersion == null ? "1.0.0" : firmwareVersion,
                maxPowerWatts == null ? 10000.0 : maxPowerWatts,
                now, null, now, null);
    }

    /**
     * Reconstruye un medidor guardado.
     *
     * <p>Tolera que el local venga nulo: los medidores dados de alta antes de
     * que el local fuera obligatorio siguen en la base y tienen que poder
     * leerse. Lo que no se admite es registrar uno nuevo sin local.
     */
    public static EnergyMeter rehydrate(UUID id, String userId, String siteId, String zoneId,
                                        String meterSerial, String model, String brand,
                                        String location, MeterStatus status,
                                        String firmwareVersion, double maxPowerWatts,
                                        Instant registeredAt, Instant lastSeenAt, Instant updatedAt,
                                        Instant unlinkedAt) {
        return new EnergyMeter(id, userId, siteId, zoneId, meterSerial, model, brand, location,
                status, firmwareVersion, maxPowerWatts, registeredAt, lastSeenAt, updatedAt,
                unlinkedAt);
    }

    /**
     * Reasigna el medidor a otra zona del mismo local.
     *
     * <p>Pasar nulo lo deja sin zona, que es un estado valido: el medidor sigue
     * siendo del local. Quien llama es responsable de haber comprobado que la
     * zona pertenece a este local; el dominio no conoce el modulo de
     * organizaciones.
     */
    public void reasignarAZona(String zoneId) {
        this.zoneId = limpiar(zoneId);
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == MeterStatus.ACTIVE;
    }

    /** Marca el instante en que el medidor reporto por ultima vez. */
    public void updateLastSeen() {
        this.lastSeenAt = Instant.now();
    }

    public void deactivate() {
        this.status = MeterStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    public boolean isUnlinked() {
        return this.unlinkedAt != null;
    }

    /**
     * Desvincula el medidor de la cuenta. No borra nada: las lecturas y los
     * consumos siguen en la base con su historico, y el medidor puede volver a
     * vincularse registrando el mismo numero de serie. Es idempotente, para que
     * repetir la peticion no mueva la fecha.
     */
    public void unlink() {
        if (isUnlinked()) {
            return;
        }
        this.status = MeterStatus.INACTIVE;
        this.unlinkedAt = Instant.now();
        this.updatedAt = this.unlinkedAt;
    }

    /** Vuelve a vincular un medidor desvinculado, posiblemente a otra cuenta. */
    public void relink(String userId) {
        if (isBlank(userId)) {
            throw AppException.validation("user_id is required");
        }
        this.userId = userId.trim();
        this.status = MeterStatus.ACTIVE;
        this.unlinkedAt = null;
        this.updatedAt = Instant.now();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Texto en blanco y ausencia son lo mismo para un identificador opcional. */
    private static String limpiar(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
