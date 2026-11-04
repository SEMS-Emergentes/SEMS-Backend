package com.sems.analytics.application;

import com.sems.analytics.domain.model.entities.Anomaly;
import com.sems.analytics.domain.model.entities.Recommendation;
import com.sems.analytics.domain.repositories.AnalyticsRepositories;
import com.sems.energy.interfaces.acl.EnergyAcl;
import com.sems.organizations.interfaces.acl.OrganizationsAcl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produce las anomalias y las recomendaciones a partir del consumo medido.
 *
 * <p>Hasta ahora las dos cosas se podian guardar y consultar, pero nadie las
 * generaba: los endpoints de alta esperaban a un productor que no existia, de
 * modo que las dos pantallas estaban condenadas a salir vacias por mucho que el
 * cliente midiera. Esto es ese productor.
 *
 * <p>Son reglas explicitas y pocas, no un modelo. Esa es la eleccion
 * deliberada: una recomendacion que el cliente no entiende no la aplica, y una
 * anomalia que no sabe explicar por que lo es acaba ignorada. Cada regla de
 * aqui se puede defender en una frase, y el texto que genera dice el numero que
 * la disparo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightEngine {

    /** Ventana de historia que se mira. Un mes es el periodo del recibo. */
    private static final int DIAS = 30;
    /** Dias minimos de historia antes de afirmar que algo es raro. */
    private static final int DIAS_MINIMOS = 7;
    /** Cuantas desviaciones tipicas por encima de su media hacen sospechoso un dia. */
    private static final double SIGMAS = 2.0;
    /** Y ademas tiene que separarse esto de la media, para no marcar ruido. */
    private static final double DESVIO_MINIMO_PORCENTAJE = 25.0;
    private static final double DESVIO_MINIMO_KWH = 5.0;
    /** Parte del local a partir de la cual una zona merece que se la mire. */
    private static final double ZONA_DOMINANTE = 0.40;
    /** Parte del consumo en hora punta a partir de la cual conviene desplazar carga. */
    private static final double PUNTA_ALTA = 0.25;
    /** Peru esta en UTC-5 todo el ano: el dia natural se corta ahi. */
    private static final java.time.ZoneOffset PERU = java.time.ZoneOffset.ofHours(-5);

    private final AnalyticsRepositories.AnomalyRepository anomalies;
    private final AnalyticsRepositories.RecommendationRepository recommendations;
    private final AnalyticsService analytics;
    private final EnergyAcl energia;
    private final OrganizationsAcl organizaciones;

    /**
     * Recalcula los hallazgos del usuario con lo medido hasta hoy.
     *
     * <p>Es idempotente dentro del dia: si ya se genero el mismo hallazgo hoy,
     * no se duplica. Sin esa condicion, con una lectura cada pocos minutos la
     * lista crecería hasta ser inservible.
     */
    @Transactional
    public Hallazgos recalcular(String userId) {
        // Solo dias cerrados. El dia en curso esta a medias y compararlo con
        // medias de dias completos daria un desvio enorme a media manana y
        // ninguno a medianoche. Ademas hace que el resultado no cambie dentro
        // del mismo dia, que es lo que permite no duplicar hallazgos sin tener
        // que reescribir los de antes.
        LocalDate hoy = LocalDate.now(PERU);
        List<EnergyAcl.DailyUsage> consumo = energia.dailyUsage(userId, DIAS).stream()
                .filter(d -> d.day().isBefore(hoy))
                .toList();
        if (consumo.isEmpty()) {
            return new Hallazgos(List.of(), List.of());
        }
        proyectarFactura(userId, consumo);
        return new Hallazgos(detectarAnomalias(userId, consumo),
                proponerAhorros(userId, consumo));
    }

    // ------------------------------------------------------------ proyeccion

    /**
     * Proyecta el recibo del mes de cada local con lo medido hasta hoy.
     *
     * <p>La pantalla de analitica leia la proyeccion de la base de datos y
     * nadie la escribia nunca, asi que mostraba una raya para siempre. El
     * calculo ya existia en {@code forecastSiteBill}; lo que faltaba era que
     * alguien le pasara el consumo medido.
     *
     * <p>Se extrapola lo medido a los dias del mes: con una semana de datos la
     * cifra es orientativa, y el margen de error que se guarda lo dice.
     */
    private void proyectarFactura(String userId, List<EnergyAcl.DailyUsage> consumo) {
        Map<UUID, List<EnergyAcl.DailyUsage>> porLocal = new LinkedHashMap<>();
        for (EnergyAcl.DailyUsage d : consumo) {
            if (d.siteId() != null) {
                porLocal.computeIfAbsent(d.siteId(), k -> new ArrayList<>()).add(d);
            }
        }

        LocalDate hoy = LocalDate.now(PERU);
        for (var entrada : porLocal.entrySet()) {
            UUID local = entrada.getKey();
            var precios = energia.pricesOf(local).orElse(null);
            BigDecimal contratada = organizaciones.contractedPowerOf(local).orElse(null);
            if (precios == null || contratada == null) {
                continue;
            }

            List<EnergyAcl.DailyUsage> dias = entrada.getValue();
            long diasMedidos = dias.stream().map(EnergyAcl.DailyUsage::day).distinct().count();
            if (diasMedidos == 0) {
                continue;
            }
            if (yaHayProyeccionHoy(userId, local)) {
                continue;
            }

            int diasDelMes = hoy.lengthOfMonth();
            double factor = (double) diasDelMes / diasMedidos;
            double punta = dias.stream().mapToDouble(EnergyAcl.DailyUsage::peakKwh).sum() * factor;
            double fuera = dias.stream()
                    .mapToDouble(d -> d.kwh() - d.peakKwh()).sum() * factor;
            // La demanda no se extrapola: el cargo lo fija el pico ya ocurrido,
            // y multiplicarlo por los dias que faltan no tendria sentido.
            double pico = dias.stream().mapToDouble(EnergyAcl.DailyUsage::maxPowerKw).max()
                    .orElse(0);

            // Cuantos menos dias medidos, mas se parece esto a una apuesta.
            double margen = Math.max(5, Math.min(40, 100.0 / Math.max(1, diasMedidos)));

            analytics.forecastSiteBill(userId, local.toString(), precios.tariffCategory(),
                    contratada.doubleValue(), hoy.getYear(), hoy.getMonthValue(),
                    hoy.withDayOfMonth(1).atStartOfDay().toInstant(PERU),
                    hoy.withDayOfMonth(diasDelMes).atTime(23, 59)
                            .toInstant(PERU),
                    redondea(punta), redondea(fuera), pico, redondea(margen));
        }
    }

    private boolean yaHayProyeccionHoy(String userId, UUID siteId) {
        LocalDate hoy = LocalDate.now(PERU);
        return analytics.predictionsByUser(userId).stream()
                .anyMatch(p -> LocalDate.ofInstant(p.getCreatedAt(), PERU)
                        .equals(hoy)
                        && siteId.toString().equals(p.getSiteId()));
    }

    // ------------------------------------------------------------- anomalias

    /**
     * Un dia es anomalo cuando se sale del comportamiento del propio medidor.
     *
     * <p>Se compara cada medidor consigo mismo y no con los demas: una camara
     * de frio consume mucho mas que la iluminacion de la oficina sin que eso
     * sea un problema. Lo que importa es el cambio respecto a lo habitual de
     * ese punto de medida.
     */
    private List<Anomaly> detectarAnomalias(String userId, List<EnergyAcl.DailyUsage> consumo) {
        Map<UUID, List<EnergyAcl.DailyUsage>> porMedidor = new LinkedHashMap<>();
        for (EnergyAcl.DailyUsage d : consumo) {
            porMedidor.computeIfAbsent(d.meterId(), k -> new ArrayList<>()).add(d);
        }

        List<Anomaly> nuevas = new ArrayList<>();
        for (var entrada : porMedidor.entrySet()) {
            List<EnergyAcl.DailyUsage> dias = entrada.getValue();
            dias.sort(Comparator.comparing(EnergyAcl.DailyUsage::day));
            if (dias.size() < DIAS_MINIMOS + 1) {
                continue;
            }

            EnergyAcl.DailyUsage ultimo = dias.get(dias.size() - 1);
            List<EnergyAcl.DailyUsage> anteriores = dias.subList(0, dias.size() - 1);

            double media = anteriores.stream().mapToDouble(EnergyAcl.DailyUsage::kwh).average()
                    .orElse(0);
            if (media <= 0) {
                continue;
            }
            double varianza = anteriores.stream()
                    .mapToDouble(d -> Math.pow(d.kwh() - media, 2)).average().orElse(0);
            double sigma = Math.sqrt(varianza);

            double exceso = ultimo.kwh() - media;
            double desvio = exceso / media * 100.0;
            boolean fueraDeRango = exceso > SIGMAS * sigma
                    && desvio >= DESVIO_MINIMO_PORCENTAJE
                    && exceso >= DESVIO_MINIMO_KWH;
            if (!fueraDeRango) {
                continue;
            }

            String descripcion = String.format(
                    "El %s midio %.1f kWh, un %.0f%% por encima de los %.1f kWh que viene "
                            + "consumiendo de media en los ultimos %d dias.",
                    ultimo.day(), ultimo.kwh(), desvio, media, anteriores.size());
            String severidad = desvio >= 60 ? "high" : desvio >= 40 ? "medium" : "low";

            if (yaHayAnomaliaHoy(userId, ultimo.meterId())) {
                continue;
            }
            // El campo se llama deviceId por historia; aqui lleva el medidor,
            // que es el punto de medida del que salio el consumo.
            nuevas.add(anomalies.save(Anomaly.detect(userId, ultimo.meterId().toString(),
                    "consumption_spike", descripcion, severidad, ultimo.kwh(),
                    redondea(media))));
        }
        return nuevas;
    }

    private boolean yaHayAnomaliaHoy(String userId, UUID meterId) {
        LocalDate hoy = LocalDate.now(PERU);
        return anomalies.findByUserId(userId).stream()
                .anyMatch(a -> meterId.toString().equals(a.getDeviceId())
                        && LocalDate.ofInstant(a.getDetectedAt(), PERU)
                        .equals(hoy));
    }

    // -------------------------------------------------------- recomendaciones

    /**
     * Propone donde hay ahorro, con el numero que lo justifica.
     *
     * <p>Tres reglas, cada una atada a como se factura un suministro comercial:
     * donde se concentra el consumo, cuanto cae en la franja cara, y si el pico
     * de potencia esta cerca de lo contratado.
     */
    private List<Recommendation> proponerAhorros(String userId,
                                                 List<EnergyAcl.DailyUsage> consumo) {
        Map<UUID, List<EnergyAcl.DailyUsage>> porLocal = new LinkedHashMap<>();
        for (EnergyAcl.DailyUsage d : consumo) {
            if (d.siteId() != null) {
                porLocal.computeIfAbsent(d.siteId(), k -> new ArrayList<>()).add(d);
            }
        }

        List<Recommendation> nuevas = new ArrayList<>();
        for (var entrada : porLocal.entrySet()) {
            UUID local = entrada.getKey();
            List<EnergyAcl.DailyUsage> dias = entrada.getValue();
            var precios = energia.pricesOf(local).orElse(null);
            if (precios == null) {
                continue;
            }
            String nombreLocal = organizaciones.siteSummary(local)
                    .map(OrganizationsAcl.SiteSummary::name).orElse("el local");

            double totalKwh = dias.stream().mapToDouble(EnergyAcl.DailyUsage::kwh).sum();
            double puntaKwh = dias.stream().mapToDouble(EnergyAcl.DailyUsage::peakKwh).sum();
            if (totalKwh <= 0) {
                continue;
            }

            agregar(nuevas, zonaDominante(userId, local, nombreLocal, dias, totalKwh, precios));
            agregar(nuevas, consumoEnPunta(userId, local, nombreLocal, totalKwh, puntaKwh, precios));
            agregar(nuevas, picoDePotencia(userId, local, nombreLocal, dias, precios));
        }
        return nuevas;
    }

    /** Regla 1: una zona se lleva la mayor parte del local. */
    private Recommendation zonaDominante(String userId, UUID local, String nombreLocal,
                                         List<EnergyAcl.DailyUsage> dias, double totalKwh,
                                         EnergyAcl.SitePrices precios) {
        Map<UUID, Double> porZona = new LinkedHashMap<>();
        for (EnergyAcl.DailyUsage d : dias) {
            if (d.zoneId() != null) {
                porZona.merge(d.zoneId(), d.kwh(), Double::sum);
            }
        }
        var mayor = porZona.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (mayor == null || mayor.getValue() / totalKwh < ZONA_DOMINANTE) {
            return null;
        }

        var zona = organizaciones.activeZonesOf(local).stream()
                .filter(z -> z.zoneId().equals(mayor.getKey())).findFirst().orElse(null);
        if (zona == null) {
            return null;
        }
        double parte = mayor.getValue() / totalKwh * 100.0;
        // Un 8% es lo que suele dar revisar cierres, sondas y ciclos de
        // desescarche en una instalacion que nadie ha tocado en meses. Es una
        // estimacion prudente y se presenta como tal.
        double ahorroKwh = mayor.getValue() * 0.08;
        BigDecimal ahorro = BigDecimal.valueOf(ahorroKwh)
                .multiply(precios.offPeakPerKwh()).setScale(2, RoundingMode.HALF_UP);

        String titulo = "Revisa " + zona.name() + " en " + nombreLocal;
        if (yaHayRecomendacionHoy(userId, "zone_concentration", titulo)) {
            return null;
        }
        String descripcion = String.format(
                "%s concentra el %.0f%% del consumo del local (%.0f kWh en el periodo). "
                        + "Una revision de equipos y horarios en esa zona es donde mas rinde.",
                zona.name(), parte, mayor.getValue());
        return recommendations.save(Recommendation.create(userId, null, "zone_concentration",
                titulo, descripcion, redondea(ahorroKwh), ahorro.doubleValue(),
                precios.currency()));
    }

    /** Regla 2: demasiado consumo en la franja cara. */
    private Recommendation consumoEnPunta(String userId, UUID local, String nombreLocal,
                                          double totalKwh, double puntaKwh,
                                          EnergyAcl.SitePrices precios) {
        double parte = puntaKwh / totalKwh;
        if (parte < PUNTA_ALTA) {
            return null;
        }
        // Desplazar la quinta parte de lo que hoy cae en punta: lo que suele
        // poderse mover sin tocar el horario comercial (prerrefrigerar antes de
        // las 18:00, retrasar lavados y hornos).
        double movible = puntaKwh * 0.20;
        BigDecimal ahorro = BigDecimal.valueOf(movible)
                .multiply(precios.peakPerKwh().subtract(precios.offPeakPerKwh()))
                .setScale(2, RoundingMode.HALF_UP);
        if (ahorro.signum() <= 0) {
            return null;
        }

        String titulo = "Desplaza consumo fuera de punta en " + nombreLocal;
        if (yaHayRecomendacionHoy(userId, "peak_shift", titulo)) {
            return null;
        }
        String descripcion = String.format(
                "El %.0f%% del consumo cae entre las 18:00 y las 23:00, que es la franja cara "
                        + "(%s/kWh frente a %s/kWh). Mover una quinta parte a otra hora ahorra "
                        + "sin consumir menos.",
                parte * 100, precios.peakPerKwh(), precios.offPeakPerKwh());
        return recommendations.save(Recommendation.create(userId, null, "peak_shift", titulo,
                descripcion, redondea(movible), ahorro.doubleValue(), precios.currency()));
    }

    /** Regla 3: el pico de potencia roza lo contratado. */
    private Recommendation picoDePotencia(String userId, UUID local, String nombreLocal,
                                          List<EnergyAcl.DailyUsage> dias,
                                          EnergyAcl.SitePrices precios) {
        double pico = dias.stream().mapToDouble(EnergyAcl.DailyUsage::maxPowerKw).max().orElse(0);
        BigDecimal contratada = organizaciones.contractedPowerOf(local).orElse(null);
        if (contratada == null || contratada.signum() <= 0 || pico <= 0) {
            return null;
        }
        double ocupacion = pico / contratada.doubleValue();
        if (ocupacion < 0.85) {
            return null;
        }

        String titulo = "Escalona los arranques en " + nombreLocal;
        if (yaHayRecomendacionHoy(userId, "demand_peak", titulo)) {
            return null;
        }
        // Rebajar el pico un 10% ahorra ese 10% del cargo por potencia del mes,
        // que se paga por el pico y no por la energia.
        BigDecimal ahorro = contratada.multiply(BigDecimal.valueOf(ocupacion * 0.10))
                .multiply(precios.powerPerKwMonth()).setScale(2, RoundingMode.HALF_UP);
        String descripcion = String.format(
                "El pico medido fue de %.1f kW sobre %.1f kW contratados (%.0f%%). El cargo por "
                        + "potencia del mes lo fija ese pico, asi que arrancar los equipos "
                        + "escalonados rebaja la factura entera.",
                pico, contratada.doubleValue(), ocupacion * 100);
        return recommendations.save(Recommendation.create(userId, null, "demand_peak", titulo,
                descripcion, 0, ahorro.doubleValue(), precios.currency()));
    }

    private boolean yaHayRecomendacionHoy(String userId, String tipo, String titulo) {
        LocalDate hoy = LocalDate.now(PERU);
        return recommendations.findByUserId(userId).stream()
                .anyMatch(r -> tipo.equals(r.getRecommendationType())
                        && titulo.equals(r.getTitle())
                        && LocalDate.ofInstant(r.getGeneratedAt(), PERU)
                        .equals(hoy));
    }

    private static void agregar(List<Recommendation> destino, Recommendation r) {
        if (r != null) {
            destino.add(r);
        }
    }

    private static double redondea(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Lo producido en una pasada. */
    public record Hallazgos(List<Anomaly> anomalias, List<Recommendation> recomendaciones) {
    }
}
