package com.sems.energy;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.interfaces.rest.resources.EnergyResources.MeterResponse;
import com.sems.energy.interfaces.rest.resources.EnergyResources.RegisterMeterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El frontend fue escrito contra un backend en FastAPI, que serializa en
 * snake_case. Estas pruebas fijan ese contrato para que un cambio accidental
 * en la configuracion de Jackson no rompa la aplicacion en produccion.
 */
class EnergyContractTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("La respuesta del medidor se serializa en snake_case")
    void meterResponseUsesSnakeCase() throws Exception {
        EnergyMeter meter = EnergyMeter.register("user-1", "site-1", "zone-1", "EOS-123",
                "EOS-X", "EOS", "Camara de frio 1", "1.0.0", 10000.0);

        String json = mapper.writeValueAsString(MeterResponse.from(meter));

        assertTrue(json.contains("\"user_id\""), "falta user_id: " + json);
        assertTrue(json.contains("\"site_id\""), "falta site_id: " + json);
        assertTrue(json.contains("\"zone_id\""), "falta zone_id: " + json);
        assertTrue(json.contains("\"meter_serial\""), "falta meter_serial");
        assertTrue(json.contains("\"max_power_watts\""), "falta max_power_watts");
        assertTrue(json.contains("\"registered_at\""), "falta registered_at");
        assertFalse(json.contains("\"userId\""), "no debe aparecer camelCase");
        assertTrue(json.contains("\"active\""), "el estado va en minusculas");
    }

    @Test
    @DisplayName("La peticion de registro se deserializa desde snake_case")
    void registerRequestReadsSnakeCase() throws Exception {
        String body = """
                {"user_id":"u-1","meter_serial":"EOS-9","model":"EOS-X",
                 "brand":"EOS","location":"Sala","max_power_watts":8000.0}
                """;

        RegisterMeterRequest request = mapper.readValue(body, RegisterMeterRequest.class);

        assertEquals("u-1", request.userId());
        assertEquals("EOS-9", request.meterSerial());
        assertEquals("Sala", request.location());
        assertEquals(8000.0, request.maxPowerWatts());
    }

    @Test
    @DisplayName("Un medidor recien registrado esta activo y sin ultima lectura")
    void newMeterStartsActive() {
        EnergyMeter meter = EnergyMeter.register("u", "site-1", null, "S1", "m", "b", "l",
                null, null);

        assertTrue(meter.isActive());
        assertNull(meter.getLastSeenAt());
        assertEquals("1.0.0", meter.getFirmwareVersion());
        assertEquals(10000.0, meter.getMaxPowerWatts());

        meter.deactivate();
        assertFalse(meter.isActive());
    }
}
