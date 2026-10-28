package com.sems.energy.interfaces.rest;

import com.sems.energy.application.EnergyCommandService;
import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.interfaces.rest.resources.EnergyResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Tarifa vigente y consumo por dispositivo.
 *
 * <p>El precio de referencia de {@code /pricing/current} es un unico numero
 * por kWh, heredado del producto domestico. Un suministro comercial no se
 * factura asi: la energia vale distinto dentro y fuera de la hora punta y
 * ademas se paga por la potencia contratada. Quien quiera convertir kWh en
 * soles debe usar la tarifa del local ({@code /energy/tariffs/{categoria}}) o
 * el coste que ya viene en cada lectura.
 */
@Tag(name = "Energy Pricing", description = "Electricity tariff and per-device consumption")
@RestController
@RequestMapping("/api/v1/energy")
@RequiredArgsConstructor
public class EnergyPricingController {

    private final EnergyCommandService commands;
    private final EnergyQueryService queries;

    /**
     * Precio plano de referencia.
     *
     * <p>Se mantiene por compatibilidad, pero no describe lo que paga un local:
     * usarlo para calcular costes es lo que hacia que la misma pantalla
     * mostrara dos cifras distintas para el mismo consumo.
     *
     * @deprecated usar la tarifa comercial del local.
     */
    @Deprecated(since = "2026.10")
    @Operation(summary = "Current flat reference price (deprecated: use the site tariff)",
            deprecated = true)
    @GetMapping("/pricing/current")
    public PricingResponse currentPricing() {
        return PricingResponse.from(commands.currentPrice());
    }

    @Operation(summary = "Current commercial tariff for a category of the schedule")
    @GetMapping("/tariffs/{tariffCategory}")
    public TariffResponse currentTariff(@PathVariable String tariffCategory) {
        return TariffResponse.from(commands.currentTariff(tariffCategory));
    }

    @Operation(summary = "Estimates a site's monthly bill")
    @PostMapping("/bill-estimate")
    public BillEstimateResponse estimateBill(@Valid @RequestBody EstimateBillRequest request) {
        return BillEstimateResponse.from(commands.estimateBill(request.tariffCategory(),
                request.kwhPeak(), request.kwhOffPeak(), request.maxDemandKw(),
                request.contractedPowerKw()));
    }

    @Operation(summary = "Current consumption of a device")
    @PreAuthorize("@accesoEnergia.lecturasDelDispositivo(#deviceId)")
    @GetMapping("/devices/{deviceId}/consumption/current")
    public ReadingResponse currentConsumption(@PathVariable String deviceId) {
        return ReadingResponse.from(queries.latestByDevice(deviceId));
    }

    @Operation(summary = "Consumption history of a device")
    @PreAuthorize("@accesoEnergia.lecturasDelDispositivo(#deviceId)")
    @GetMapping("/devices/{deviceId}/consumption/history")
    public List<ReadingResponse> consumptionHistory(@PathVariable String deviceId,
                                                    @RequestParam(defaultValue = "50") int limit,
                                                    @RequestParam(defaultValue = "0") int skip) {
        return queries.readingsByDevice(deviceId, limit, skip).stream()
                .map(ReadingResponse::from).toList();
    }
}
