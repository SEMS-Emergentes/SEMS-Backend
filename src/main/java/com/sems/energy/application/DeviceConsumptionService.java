package com.sems.energy.application;

import com.sems.devices.interfaces.acl.DevicesAcl;
import com.sems.energy.domain.model.entities.DeviceConsumption;
import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.domain.model.entities.EnergyReading;
import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.model.valueobjects.FranjaHoraria;
import com.sems.energy.domain.model.valueobjects.HorarioPunta;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.energy.domain.repositories.EnergyReadingRepository;
import com.sems.energy.domain.services.EnergyPricingProvider;
import com.sems.organizations.interfaces.acl.OrganizationsAcl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumo del periodo repartido entre los equipos de la persona.
 *
 * <p>Es el productor que faltaba. La entidad, el mapeo, el repositorio y el
 * endpoint existian, pero nadie creaba ni guardaba un solo resumen: la tarjeta
 * "por dispositivo" del panel estaba condenada a salir vacia por mucho que el
 * cliente midiera.
 *
 * <p>Se calcula al consultarlo en vez de mantenerse escrito. Las lecturas son
 * el hecho medido y el resumen es una vista de ellas: guardarlo obligaria a
 * reescribirlo en cada lectura, a elegir de antemano un periodo que luego nadie
 * puede cambiar, y a arrastrar un segundo numero para el mismo consumo que
 * acaba discrepando del primero sin que nadie se entere. El modulo ya resuelve
 * asi el reparto por zona y la comparacion entre locales.
 *
 * <p>El medidor mide un local o una zona, no un aparato: la atribucion a un
 * equipo viene del {@code device_id} opcional de la lectura. Por eso lo medido
 * sin equipo no desaparece del reparto, sale como una fila sin
 * {@code device_id} —"sin asignar"—, igual que el reparto por zona no esconde
 * los medidores que aun no tienen zona. Si se omitiera, los porcentajes de la
 * tarjeta no sumarian el consumo del local y el reparto mentiria.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceConsumptionService {

    /** Ventana por defecto: un mes, que es el periodo del recibo. */
    public static final int DIAS_POR_DEFECTO = 30;
    private static final int DIAS_MAXIMO = 365;

    private final EnergyReadingRepository readings;
    private final EnergyMeterRepository meters;
    private final EnergyPricingProvider pricing;
    private final OrganizationsAcl organizaciones;
    private final DevicesAcl dispositivos;

    /**
     * Un resumen por equipo con lecturas en el periodo, de mayor consumo a
     * menor, y uno final para lo medido sin equipo asignado.
     */
    public List<DeviceConsumption> byUser(String userId, int days) {
        return resumenes(userId, days, true);
    }

    /**
     * Los equipos que mas consumen del periodo.
     *
     * <p>Aqui lo no asignado si queda fuera, y no por comodidad: esto es un
     * ranking de equipos, y "sin asignar" no es un equipo sobre el que el
     * cliente pueda actuar. El reparto completo, con esa parte incluida, es
     * {@link #byUser(String, int)}.
     */
    public List<DeviceConsumption> topByUser(String userId, int days, int limit) {
        List<DeviceConsumption> porEquipo = resumenes(userId, days, false);
        return porEquipo.subList(0, Math.min(Math.max(limit, 0), porEquipo.size()));
    }

    // ------------------------------------------------------------------ apoyo

    private List<DeviceConsumption> resumenes(String userId, int days, boolean incluirSinAsignar) {
        int dias = dias(days);
        Instant hasta = Instant.now();
        Instant desde = hasta.minus(Duration.ofDays(dias));

        Map<String, EnergyMeter> medidores = new LinkedHashMap<>();
        for (EnergyMeter medidor : meters.findByUserId(userId)) {
            medidores.put(medidor.getId().toString(), medidor);
        }

        // El local de cada medidor se consulta una vez y no por lectura: la
        // franja horaria depende de si el suministro tiene concedida la
        // exclusion de domingos, y eso es del local.
        Map<String, OrganizationsAcl.SiteSummary> locales = new LinkedHashMap<>();
        Map<String, Acumulado> porEquipo = new LinkedHashMap<>();
        Acumulado sinAsignar = new Acumulado();

        for (EnergyReading lectura : readings.findByRange(userId, desde, hasta)) {
            OrganizationsAcl.SiteSummary local = localDe(lectura.getMeterId(), medidores, locales);
            String equipo = lectura.getDeviceId();
            Acumulado acumulado = equipo == null || equipo.isBlank()
                    ? sinAsignar
                    : porEquipo.computeIfAbsent(equipo, e -> new Acumulado());
            acumulado.suma(lectura, local == null ? null : local.tariffCategory(),
                    local != null && local.excludesSundaysFromPeak());
        }

        Map<String, String> nombres = nombresDeLosEquipos(userId);
        List<DeviceConsumption> salida = new ArrayList<>();
        porEquipo.forEach((equipo, acumulado) ->
                salida.add(resumen(userId, equipo, nombres.get(equipo), acumulado, desde, hasta)));
        salida.sort(Comparator.comparingDouble(DeviceConsumption::getTotalKwh).reversed());

        if (incluirSinAsignar && sinAsignar.lecturas > 0) {
            salida.add(resumen(userId, null, null, sinAsignar, desde, hasta));
        }
        return salida;
    }

    private DeviceConsumption resumen(String userId, String deviceId, String deviceName,
                                      Acumulado acumulado, Instant desde, Instant hasta) {
        return DeviceConsumption.create(userId, deviceId, deviceName, acumulado.medidor(),
                redondea(acumulado.kwh()), coste(acumulado), desde, hasta,
                redondea(acumulado.picoVatios), redondea(acumulado.mediaDeVatios()),
                acumulado.lecturas);
    }

    /**
     * Nombre de cada equipo de la persona, indexado por identificador.
     *
     * <p>Un equipo dado de baja ya no esta en esta lista, pero su consumo del
     * periodo si: se queda con su identificador y sin nombre, que es preferible
     * a perderlo.
     */
    private Map<String, String> nombresDeLosEquipos(String userId) {
        UUID persona = aUuid(userId);
        if (persona == null) {
            return Map.of();
        }
        Map<String, String> nombres = new LinkedHashMap<>();
        for (DevicesAcl.DeviceSummary equipo : dispositivos.devicesOf(persona)) {
            nombres.put(equipo.deviceId().toString(), equipo.name());
        }
        return nombres;
    }

    private OrganizationsAcl.SiteSummary localDe(String meterId, Map<String, EnergyMeter> medidores,
                                                 Map<String, OrganizationsAcl.SiteSummary> cache) {
        EnergyMeter medidor = meterId == null ? null : medidores.get(meterId);
        if (medidor == null || medidor.getSiteId() == null) {
            return null;
        }
        return cache.computeIfAbsent(medidor.getSiteId(),
                id -> organizaciones.siteSummary(aUuid(id)).orElse(null));
    }

    /**
     * Valora el consumo con la tarifa del local y la franja en que se midio.
     *
     * <p>Se acumula por categoria tarifaria porque lo no asignado puede venir de
     * medidores de locales distintos. Lo que no tenga tarifa reconocible se
     * valora con la tarifa media de referencia: devolver cero diria que esa
     * energia es gratis, que es peor que estimarla.
     */
    private double coste(Acumulado acumulado) {
        BigDecimal total = BigDecimal.ZERO;
        double kwhSinTarifa = 0;

        for (Map.Entry<String, double[]> entrada : acumulado.porTarifa.entrySet()) {
            double punta = entrada.getValue()[0];
            double fuera = entrada.getValue()[1];
            CommercialTariff tarifa = tarifaDe(entrada.getKey());
            if (tarifa == null) {
                kwhSinTarifa += punta + fuera;
                continue;
            }
            total = total.add(tarifa.costoDeEnergia(BigDecimal.valueOf(punta),
                    BigDecimal.valueOf(fuera)));
        }

        total = total.add(BigDecimal.valueOf(kwhSinTarifa * DeviceConsumption.DEFAULT_TARIFF));
        return total.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private CommercialTariff tarifaDe(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return null;
        }
        try {
            return pricing.currentTariff(categoria);
        } catch (RuntimeException sinTarifa) {
            // Un consumo sin tarifa reconocida sigue siendo consumo que mostrar;
            // lo que no se puede es ponerle precio exacto.
            return null;
        }
    }

    private int dias(int days) {
        if (days <= 0) {
            return DIAS_POR_DEFECTO;
        }
        return Math.min(days, DIAS_MAXIMO);
    }

    private static double redondea(double valor) {
        return Math.round(valor * 100.0) / 100.0;
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

    /** Acumulador de un equipo mientras se recorren sus lecturas. */
    private static final class Acumulado {

        /** Categoria tarifaria -> {kWh en punta, kWh fuera de punta}. */
        private final Map<String, double[]> porTarifa = new LinkedHashMap<>();
        private final Set<String> medidores = new LinkedHashSet<>();
        private double picoVatios;
        private double sumaDeVatios;
        private int lecturas;

        void suma(EnergyReading lectura, String categoriaTarifaria, boolean excluyeDomingos) {
            double kwh = lectura.getMeasurement().energyKwh();
            double[] franjas = porTarifa.computeIfAbsent(
                    categoriaTarifaria == null ? "" : categoriaTarifaria, c -> new double[2]);
            if (HorarioPunta.franjaDe(lectura.getTimestamp(), excluyeDomingos)
                    == FranjaHoraria.PUNTA) {
                franjas[0] += kwh;
            } else {
                franjas[1] += kwh;
            }

            picoVatios = Math.max(picoVatios, lectura.getMeasurement().powerWatts());
            sumaDeVatios += lectura.getMeasurement().powerWatts();
            lecturas++;
            if (lectura.getMeterId() != null) {
                medidores.add(lectura.getMeterId());
            }
        }

        double kwh() {
            return porTarifa.values().stream().mapToDouble(f -> f[0] + f[1]).sum();
        }

        double mediaDeVatios() {
            return lecturas == 0 ? 0 : sumaDeVatios / lecturas;
        }

        /**
         * El medidor del que vino el consumo, o vacio si vino de varios.
         *
         * <p>Lo normal es un equipo colgado de un punto de medida. Si hay
         * varios, nombrar uno cualquiera seria mentir sobre el origen del dato.
         */
        String medidor() {
            return medidores.size() == 1 ? medidores.iterator().next() : null;
        }
    }
}
