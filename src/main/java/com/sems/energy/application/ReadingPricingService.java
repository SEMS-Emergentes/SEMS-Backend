package com.sems.energy.application;

import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.domain.model.entities.EnergyReading;
import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.model.valueobjects.FranjaHoraria;
import com.sems.energy.domain.model.valueobjects.HorarioPunta;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.energy.domain.services.EnergyPricingProvider;
import com.sems.organizations.interfaces.acl.OrganizationsAcl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pone precio a una lectura con la tarifa del local que la produjo.
 *
 * <p>Antes el coste lo calculaba el cliente multiplicando los kWh por un precio
 * unico y plano, herencia del producto domestico. Para un suministro comercial
 * eso es falso por dos motivos: la energia vale distinto dentro y fuera de la
 * hora punta, y cada local se factura con la categoria que tenga contratada.
 * Un mismo kWh no cuesta lo mismo a las once de la manana que a las ocho de la
 * noche, ni en un MT2 que en un BT5B.
 *
 * <p>Quien conoce ambos datos es el backend: el medidor sabe de que local es y
 * el local sabe su categoria. Calcularlo aqui evita que cada pantalla invente
 * su propio precio y que dos cifras de la misma aplicacion no cuadren.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingPricingService {

    private final EnergyMeterRepository meters;
    private final EnergyPricingProvider pricing;
    private final OrganizationsAcl organizaciones;

    /**
     * Coste estimado de cada lectura, en el mismo orden que la lista recibida.
     *
     * <p>Se resuelve en lote porque un listado trae cientos de lecturas de unos
     * pocos medidores: buscar el local y la tarifa una vez por medidor, y no
     * una vez por lectura, es la diferencia entre una consulta y doscientas.
     */
    public List<BigDecimal> costOf(List<EnergyReading> lecturas) {
        Map<String, Precio> porMedidor = new HashMap<>();
        return lecturas.stream().map(l -> costOf(l, porMedidor)).toList();
    }

    /** Coste estimado de una lectura suelta. */
    public BigDecimal costOf(EnergyReading lectura) {
        return costOf(lectura, new HashMap<>());
    }

    private BigDecimal costOf(EnergyReading lectura, Map<String, Precio> cache) {
        if (lectura.getMeterId() == null) {
            return null;
        }
        Precio precio = cache.computeIfAbsent(lectura.getMeterId(), this::precioDelMedidor);
        if (precio == null) {
            return null;
        }
        boolean punta = HorarioPunta.franjaDe(lectura.getTimestamp(), precio.excluyeDomingos())
                == FranjaHoraria.PUNTA;
        BigDecimal porKwh = punta ? precio.punta() : precio.fueraDePunta();
        return BigDecimal.valueOf(lectura.getMeasurement().energyKwh())
                .multiply(porKwh)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Precio precioDelMedidor(String meterId) {
        UUID id = aUuid(meterId);
        if (id == null) {
            return null;
        }
        EnergyMeter medidor = meters.findById(id).orElse(null);
        if (medidor == null || medidor.getSiteId() == null) {
            return null;
        }
        UUID local = aUuid(medidor.getSiteId());
        if (local == null) {
            return null;
        }
        // Un local archivado conserva sus lecturas y su historia de costes, asi
        // que la tarifa se busca por el resumen del local; si ya no se puede
        // resolver, la lectura se devuelve sin coste en vez de con uno inventado.
        var resumen = organizaciones.siteSummary(local).orElse(null);
        if (resumen == null || resumen.tariffCategory() == null) {
            return null;
        }
        try {
            CommercialTariff tarifa = pricing.currentTariff(resumen.tariffCategory());
            return new Precio(tarifa.energiaPuntaPorKwh(), tarifa.energiaFueraDePuntaPorKwh(),
                    resumen.excludesSundaysFromPeak());
        } catch (RuntimeException sinTarifa) {
            return null;
        }
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

    private record Precio(BigDecimal punta, BigDecimal fueraDePunta, boolean excluyeDomingos) {
    }
}
