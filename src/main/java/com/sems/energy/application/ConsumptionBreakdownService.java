package com.sems.energy.application;

import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.domain.model.entities.EnergyReading;
import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.model.valueobjects.FranjaHoraria;
import com.sems.energy.domain.model.valueobjects.HorarioPunta;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.energy.domain.repositories.EnergyReadingRepository;
import com.sems.energy.domain.services.EnergyPricingProvider;
import com.sems.organizations.interfaces.acl.OrganizationsAcl;
import com.sems.shared.errors.AppException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reparto del consumo: por zona dentro de un local, y entre los locales de una
 * organizacion.
 *
 * <p>Las dos preguntas se contestan igual —agrupar lecturas y sumarlas— pero
 * responden a cosas distintas. Dentro de un local interesa <i>donde</i> se gasta:
 * las camaras de frio y la climatizacion suelen llevarse la mayor parte, y verlo
 * en reparto es lo que permite decidir donde intervenir. Entre locales interesa
 * <i>cual</i> gasta de mas, y ahi el total engana: un local grande consume mas
 * que uno pequeno sin que eso signifique nada. Por eso la comparacion entre
 * locales tambien da el consumo por kW contratado, que si es comparable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsumptionBreakdownService {

    /** Ventana por defecto: un mes, que es el periodo del recibo. */
    public static final int DIAS_POR_DEFECTO = 30;
    private static final int DIAS_MAXIMO = 365;

    private final EnergyMeterRepository meters;
    private final EnergyReadingRepository readings;
    private final EnergyPricingProvider pricing;
    private final OrganizationsAcl organizaciones;

    /** Consumo de un local repartido entre sus zonas. */
    public ZoneBreakdown byZone(UUID siteId, int days) {
        OrganizationsAcl.SiteSummary local = organizaciones.siteSummary(siteId)
                .orElseThrow(() -> AppException.notFound("site '" + siteId + "' not found"));
        Instant desde = desde(days);

        List<EnergyMeter> medidores = meters.findBySiteId(siteId.toString());
        Map<String, UUID> zonaDelMedidor = new LinkedHashMap<>();
        for (EnergyMeter medidor : medidores) {
            zonaDelMedidor.put(medidor.getId().toString(), aUuid(medidor.getZoneId()));
        }

        Map<UUID, Consumo> porZona = new LinkedHashMap<>();
        Consumo sinZona = new Consumo();
        for (EnergyReading lectura : lecturas(zonaDelMedidor.keySet(), desde)) {
            UUID zona = zonaDelMedidor.get(lectura.getMeterId());
            Consumo acumulado = zona == null ? sinZona
                    : porZona.computeIfAbsent(zona, z -> new Consumo());
            acumulado.suma(lectura, local.excludesSundaysFromPeak());
        }

        Map<UUID, Integer> medidoresPorZona = new LinkedHashMap<>();
        int medidoresSinZona = 0;
        for (UUID zona : zonaDelMedidor.values()) {
            if (zona == null) {
                medidoresSinZona++;
            } else {
                medidoresPorZona.merge(zona, 1, Integer::sum);
            }
        }

        CommercialTariff tarifa = tarifaDe(local.tariffCategory());
        double totalKwh = porZona.values().stream().mapToDouble(Consumo::kwh).sum() + sinZona.kwh();

        List<ZoneShare> zonas = new ArrayList<>();
        for (OrganizationsAcl.ZoneSummary zona : organizaciones.activeZonesOf(siteId)) {
            Consumo consumo = porZona.getOrDefault(zona.zoneId(), new Consumo());
            zonas.add(new ZoneShare(zona.zoneId(), zona.name(), zona.zoneType(),
                    medidoresPorZona.getOrDefault(zona.zoneId(), 0),
                    redondea(consumo.kwh()), porcentaje(consumo.kwh(), totalKwh),
                    coste(consumo, tarifa)));
        }
        zonas.sort((a, b) -> Double.compare(b.kwh(), a.kwh()));

        // Los medidores que aun no estan asignados a una zona no se esconden: si
        // se omitieran, los porcentajes no sumarian cien y el reparto mentiria.
        ZoneShare noAsignado = new ZoneShare(null, null, null, medidoresSinZona,
                redondea(sinZona.kwh()), porcentaje(sinZona.kwh(), totalKwh),
                coste(sinZona, tarifa));

        return new ZoneBreakdown(siteId, local.name(), dias(days), desde, Instant.now(),
                redondea(totalKwh), moneda(tarifa), zonas, noAsignado);
    }

    /** Consumo de cada local vigente de la organizacion, para compararlos. */
    public SiteComparison bySite(UUID organizationId, int days) {
        Instant desde = desde(days);
        List<SiteShare> locales = new ArrayList<>();
        double total = 0;
        String moneda = null;

        for (OrganizationsAcl.SiteSummary local : organizaciones.activeSitesOf(organizationId)) {
            List<EnergyMeter> medidores = meters.findBySiteId(local.siteId().toString());
            List<String> ids = medidores.stream().map(m -> m.getId().toString()).toList();

            Consumo consumo = new Consumo();
            for (EnergyReading lectura : lecturas(ids, desde)) {
                consumo.suma(lectura, local.excludesSundaysFromPeak());
            }

            CommercialTariff tarifa = tarifaDe(local.tariffCategory());
            if (moneda == null) {
                moneda = moneda(tarifa);
            }
            total += consumo.kwh();
            locales.add(new SiteShare(local.siteId(), local.siteCode(), local.name(),
                    local.contractedPowerKw(), local.tariffCategory(), ids.size(),
                    redondea(consumo.kwh()), 0.0, coste(consumo, tarifa),
                    porKwContratado(consumo.kwh(), local.contractedPowerKw()),
                    redondea(consumo.picoKw())));
        }

        List<SiteShare> conPorcentaje = new ArrayList<>();
        for (SiteShare local : locales) {
            conPorcentaje.add(local.conPorcentaje(porcentaje(local.kwh(), total)));
        }
        conPorcentaje.sort((a, b) -> Double.compare(b.kwh(), a.kwh()));

        return new SiteComparison(organizationId, dias(days), desde, Instant.now(),
                redondea(total), moneda, conPorcentaje);
    }

    // ------------------------------------------------------------------ apoyo

    private List<EnergyReading> lecturas(java.util.Collection<String> medidores, Instant desde) {
        return readings.findByMeters(medidores, desde);
    }

    private int dias(int days) {
        if (days <= 0) {
            return DIAS_POR_DEFECTO;
        }
        return Math.min(days, DIAS_MAXIMO);
    }

    private Instant desde(int days) {
        return Instant.now().minus(Duration.ofDays(dias(days)));
    }

    private CommercialTariff tarifaDe(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return null;
        }
        try {
            return pricing.currentTariff(categoria);
        } catch (RuntimeException sinTarifa) {
            // Un local sin tarifa reconocida sigue teniendo consumo que mostrar;
            // lo que no se puede es ponerle precio.
            return null;
        }
    }

    private String moneda(CommercialTariff tarifa) {
        return tarifa == null ? pricing.currentPrice().currency() : tarifa.currency();
    }

    private Double coste(Consumo consumo, CommercialTariff tarifa) {
        if (tarifa == null) {
            return null;
        }
        BigDecimal importe = tarifa.costoDeEnergia(
                BigDecimal.valueOf(consumo.kwhPunta()),
                BigDecimal.valueOf(consumo.kwhFueraDePunta()));
        return importe.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double porKwContratado(double kwh, BigDecimal potenciaContratada) {
        if (potenciaContratada == null || potenciaContratada.signum() <= 0) {
            return null;
        }
        return redondea(kwh / potenciaContratada.doubleValue());
    }

    private static double porcentaje(double parte, double total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(parte / total * 1000.0) / 10.0;
    }

    private static double redondea(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    /** El modulo guarda la zona como texto; un valor que no sea un UUID cuenta como sin zona. */
    private static UUID aUuid(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException noEsUuid) {
            return null;
        }
    }

    /** Acumulador de una zona o de un local mientras se recorren las lecturas. */
    private static final class Consumo {
        private double kwhPunta;
        private double kwhFueraDePunta;
        private double picoKw;

        void suma(EnergyReading lectura, boolean excluyeDomingos) {
            double kwh = lectura.getMeasurement().energyKwh();
            if (HorarioPunta.franjaDe(lectura.getTimestamp(), excluyeDomingos) == FranjaHoraria.PUNTA) {
                kwhPunta += kwh;
            } else {
                kwhFueraDePunta += kwh;
            }
            picoKw = Math.max(picoKw, lectura.getMeasurement().powerWatts() / 1000.0);
        }

        double kwh() {
            return kwhPunta + kwhFueraDePunta;
        }

        double kwhPunta() {
            return kwhPunta;
        }

        double kwhFueraDePunta() {
            return kwhFueraDePunta;
        }

        double picoKw() {
            return picoKw;
        }
    }

    // ------------------------------------------------------------- resultados

    public record ZoneShare(UUID zoneId, String zoneName, String zoneType, int meterCount,
                            double kwh, double sharePercent, Double estimatedEnergyCost) {
    }

    public record ZoneBreakdown(UUID siteId, String siteName, int days, Instant from, Instant to,
                                double totalKwh, String currency, List<ZoneShare> zones,
                                ZoneShare unassigned) {
    }

    public record SiteShare(UUID siteId, String siteCode, String siteName,
                            BigDecimal contractedPowerKw, String tariffCategory, int meterCount,
                            double kwh, double sharePercent, Double estimatedEnergyCost,
                            Double kwhPerContractedKw, double peakDemandKw) {

        SiteShare conPorcentaje(double porcentaje) {
            return new SiteShare(siteId, siteCode, siteName, contractedPowerKw, tariffCategory,
                    meterCount, kwh, porcentaje, estimatedEnergyCost, kwhPerContractedKw,
                    peakDemandKw);
        }
    }

    public record SiteComparison(UUID organizationId, int days, Instant from, Instant to,
                                 double totalKwh, String currency, List<SiteShare> sites) {
    }
}
