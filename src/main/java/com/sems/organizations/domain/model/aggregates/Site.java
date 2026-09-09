package com.sems.organizations.domain.model.aggregates;

import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.domain.model.valueobjects.TariffCategory;
import com.sems.shared.errors.AppException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Raiz del agregado Site: un local fisico con su propio suministro electrico.
 *
 * <p>Es la unidad que de verdad importa para la energia. Cada local tiene su
 * medidor, su contrato con la distribuidora y su factura, asi que es el nivel al
 * que se predice el gasto y se compara el rendimiento entre locales de la misma
 * cadena.
 *
 * <p>Es agregado aparte de {@link Organization} a proposito: una cadena puede
 * tener decenas de locales y cargarlos todos para tocar uno solo seria un
 * desperdicio. Se relacionan por identificador, no por referencia.
 */
@Getter
public class Site {

    private final UUID siteId;
    private final UUID organizationId;
    private final String siteCode;
    private String name;
    private String address;
    private String district;
    private BigDecimal floorAreaM2;
    private BigDecimal contractedPowerKw;
    private TariffCategory tariffCategory;

    /**
     * Si este suministro tiene concedida la exclusion de domingos y feriados del
     * horario de punta.
     *
     * <p>El pliego fija la hora punta de 18:00 a 23:00 <b>todos los dias</b>, y permite
     * excluir domingos y feriados solo <i>a solicitud del cliente</i>. No es el caso
     * general, asi que por defecto es falso: darlo por concedido subestimaria la
     * factura de todos los locales que no lo pidieron.
     */
    private boolean excludesSundaysFromPeak;

    private OrgStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Site(UUID siteId, UUID organizationId, String siteCode, String name, String address,
                 String district, BigDecimal floorAreaM2, BigDecimal contractedPowerKw,
                 TariffCategory tariffCategory, boolean excludesSundaysFromPeak,
                 OrgStatus status, Instant createdAt, Instant updatedAt) {
        this.siteId = siteId;
        this.organizationId = organizationId;
        this.siteCode = siteCode;
        this.name = name;
        this.address = address;
        this.district = district;
        this.floorAreaM2 = floorAreaM2;
        this.contractedPowerKw = contractedPowerKw;
        this.tariffCategory = tariffCategory;
        this.excludesSundaysFromPeak = excludesSundaysFromPeak;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Site register(UUID organizationId, String siteCode, String name, String address,
                                String district, BigDecimal floorAreaM2,
                                BigDecimal contractedPowerKw, TariffCategory tariffCategory,
                                boolean excludesSundaysFromPeak) {
        if (organizationId == null) {
            throw AppException.validation("organization_id is required");
        }
        if (siteCode == null || siteCode.isBlank()) {
            throw AppException.validation("site_code is required");
        }
        if (name == null || name.isBlank()) {
            throw AppException.validation("name is required");
        }
        validarMedidas(floorAreaM2, contractedPowerKw);

        Instant ahora = Instant.now();
        return new Site(UUID.randomUUID(), organizationId, siteCode.trim().toUpperCase(),
                name.trim(),
                address == null || address.isBlank() ? null : address.trim(),
                district == null || district.isBlank() ? null : district.trim(),
                floorAreaM2, contractedPowerKw, tariffCategory, excludesSundaysFromPeak,
                OrgStatus.ACTIVE, ahora, ahora);
    }

    public static Site rehydrate(UUID siteId, UUID organizationId, String siteCode, String name,
                                 String address, String district, BigDecimal floorAreaM2,
                                 BigDecimal contractedPowerKw, TariffCategory tariffCategory,
                                 boolean excludesSundaysFromPeak, OrgStatus status,
                                 Instant createdAt, Instant updatedAt) {
        return new Site(siteId, organizationId, siteCode, name, address, district, floorAreaM2,
                contractedPowerKw, tariffCategory, excludesSundaysFromPeak, status, createdAt,
                updatedAt);
    }

    public void updateDetails(String name, String address, String district, BigDecimal floorAreaM2,
                              BigDecimal contractedPowerKw, TariffCategory tariffCategory,
                              boolean excludesSundaysFromPeak) {
        if (name == null || name.isBlank()) {
            throw AppException.validation("name is required");
        }
        validarMedidas(floorAreaM2, contractedPowerKw);

        this.name = name.trim();
        this.address = address == null || address.isBlank() ? null : address.trim();
        this.district = district == null || district.isBlank() ? null : district.trim();
        this.floorAreaM2 = floorAreaM2;
        this.contractedPowerKw = contractedPowerKw;
        this.tariffCategory = tariffCategory;
        this.excludesSundaysFromPeak = excludesSundaysFromPeak;
        this.updatedAt = Instant.now();
    }

    private static void validarMedidas(BigDecimal floorAreaM2, BigDecimal contractedPowerKw) {
        if (contractedPowerKw == null || contractedPowerKw.signum() <= 0) {
            throw AppException.validation("contracted_power_kw must be greater than zero");
        }
        if (floorAreaM2 != null && floorAreaM2.signum() <= 0) {
            throw AppException.validation("floor_area_m2 must be greater than zero");
        }
    }

    public void archive() {
        this.status = OrgStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == OrgStatus.ACTIVE;
    }

    /**
     * Cuanto se pasa de la potencia contratada una demanda dada, en kW. Cero si
     * no la supera.
     */
    public BigDecimal excesoDePotencia(BigDecimal demandaMaximaKw) {
        if (demandaMaximaKw == null || demandaMaximaKw.compareTo(contractedPowerKw) <= 0) {
            return BigDecimal.ZERO;
        }
        return demandaMaximaKw.subtract(contractedPowerKw);
    }
}
