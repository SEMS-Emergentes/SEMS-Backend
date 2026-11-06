package com.sems.energy.interfaces.rest;

import com.sems.energy.application.ReadingIngestionService;
import com.sems.energy.application.ReadingIngestionService.IncomingReading;
import com.sems.energy.interfaces.rest.resources.EnergyResources.IngestReadingsRequest;
import com.sems.energy.interfaces.rest.resources.EnergyResources.IngestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingesta de lecturas enviadas por los medidores.
 *
 * <p>Es la unica ruta de la API que no se autentica con el JWT de una persona.
 * La credencial del aparato viaja en {@code X-Meter-Key} y de ella salen el
 * medidor y su dueno, asi que aqui no hay {@code @PreAuthorize}: no hay ningun
 * identificador en la peticion que comprobar contra quien llama.
 *
 * <p>Por eso la ruta esta declarada como publica en la cadena de filtros, igual
 * que el webhook de Stripe, que tambien se autentica por su cuenta.
 */
@Tag(name = "Meter Ingestion", description = "Readings sent by the meters themselves")
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class MeterIngestionController {

    private final ReadingIngestionService ingesta;

    @Operation(summary = "Records a batch of readings sent by an authenticated meter")
    @PostMapping("/readings")
    public IngestionResponse readings(
            @RequestHeader(name = "X-Meter-Key", required = false) String meterKey,
            @Valid @RequestBody IngestReadingsRequest request) {
        var lote = request.readings().stream()
                .map(r -> new IncomingReading(r.energyKwh(), r.powerWatts(), r.voltage(),
                        r.current(), r.frequency(), r.timestamp(), r.deviceId(), r.readingType(),
                        r.phase()))
                .toList();
        return IngestionResponse.from(ingesta.ingest(meterKey, lote));
    }
}
