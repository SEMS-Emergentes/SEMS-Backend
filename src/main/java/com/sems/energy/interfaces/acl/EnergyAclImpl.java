package com.sems.energy.interfaces.acl;

import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.domain.model.entities.EnergyReading;
import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.model.valueobjects.FranjaHoraria;
import com.sems.energy.domain.model.valueobjects.HorarioPunta;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.energy.domain.repositories.EnergyReadingRepository;
import com.sems.energy.domain.services.EnergyPricingProvider;
import com.sems.organizations.interfaces.acl.OrganizationsAcl;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementacion de la capa anticorrupcion de energia. */
@Service
@RequiredArgsConstructor
public class EnergyAclImpl implements EnergyAcl {

    /** Peru esta en UTC-5 todo el ano: el dia natural se corta ahi. */
    private static final ZoneOffset PERU = ZoneOffset.ofHours(-5);

    private final EnergyMeterRepository meters;
    private final EnergyReadingRepository readings;
    private final EnergyPricingProvider pricing;
    private final OrganizationsAcl organizaciones;

    @Override
    @Transactional(readOnly = true)
    public List<DailyUsage> dailyUsage(String userId, int days) {
        List<EnergyMeter> delUsuario = meters.findByUserId(userId);
        if (delUsuario.isEmpty()) {
            return List.of();
        }
        Map<String, EnergyMeter> porId = new LinkedHashMap<>();
        for (EnergyMeter m : delUsuario) {
            porId.put(m.getId().toString(), m);
        }

        Instant desde = Instant.now().minus(Duration.ofDays(Math.max(1, days)));
        Map<Clave, Acumulado> acumulado = new LinkedHashMap<>();

        for (EnergyReading lectura : readings.findByMeters(porId.keySet(), desde)) {
            EnergyMeter medidor = porId.get(lectura.getMeterId());
            if (medidor == null) {
                continue;
            }
            UUID local = aUuid(medidor.getSiteId());
            boolean excluyeDomingos = local != null && organizaciones.siteSummary(local)
                    .map(OrganizationsAcl.SiteSummary::excludesSundaysFromPeak).orElse(false);
            LocalDate dia = LocalDate.ofInstant(lectura.getTimestamp(), PERU);

            Clave clave = new Clave(medidor.getId(), dia);
            Acumulado a = acumulado.computeIfAbsent(clave,
                    k -> new Acumulado(local, aUuid(medidor.getZoneId())));
            double kwh = lectura.getMeasurement().energyKwh();
            a.kwh += kwh;
            if (HorarioPunta.franjaDe(lectura.getTimestamp(), excluyeDomingos)
                    == FranjaHoraria.PUNTA) {
                a.peakKwh += kwh;
            }
            a.maxPowerKw = Math.max(a.maxPowerKw, lectura.getMeasurement().powerWatts() / 1000d);
        }

        List<DailyUsage> salida = new ArrayList<>();
        acumulado.forEach((clave, a) -> salida.add(new DailyUsage(clave.meterId(), a.siteId,
                a.zoneId, clave.dia(), redondea(a.kwh), redondea(a.peakKwh),
                redondea(a.maxPowerKw))));
        salida.sort(java.util.Comparator.comparing(DailyUsage::day));
        return salida;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SitePrices> pricesOf(UUID siteId) {
        return organizaciones.siteSummary(siteId)
                .filter(s -> s.tariffCategory() != null)
                .flatMap(s -> {
                    try {
                        CommercialTariff t = pricing.currentTariff(s.tariffCategory());
                        return Optional.of(new SitePrices(siteId, s.tariffCategory(),
                                t.energiaPuntaPorKwh(), t.energiaFueraDePuntaPorKwh(),
                                t.potenciaPorKwMes(), t.currency()));
                    } catch (RuntimeException sinTarifa) {
                        return Optional.empty();
                    }
                });
    }

    private static double redondea(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

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

    private record Clave(UUID meterId, LocalDate dia) {
    }

    private static final class Acumulado {
        private final UUID siteId;
        private final UUID zoneId;
        private double kwh;
        private double peakKwh;
        private double maxPowerKw;

        private Acumulado(UUID siteId, UUID zoneId) {
            this.siteId = siteId;
            this.zoneId = zoneId;
        }
    }
}
