package com.sems.energy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Consumo del periodo repartido entre los equipos del cliente.
 *
 * <p>El endpoint, la entidad, el mapeo y el repositorio existian, pero nadie
 * producia un solo resumen: la tarjeta "por dispositivo" del panel devolvia una
 * lista vacia por mucho que el cliente midiera con {@code device_id}. Estas
 * pruebas fallan contra ese codigo.
 *
 * <p>El matiz que se vigila aqui es el del dominio: el medidor mide un local o
 * una zona, no un aparato. La atribucion a un equipo viene del {@code
 * device_id} opcional de la lectura, asi que lo medido sin equipo tiene que
 * seguir estando en el reparto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsumoPorDispositivoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneOffset PERU = ZoneOffset.ofHours(-5);

    @Autowired
    private MockMvc mvc;

    private String token;
    private String userId;
    private String local;
    private String medidor;
    private String camara;
    private String climatizacion;

    @BeforeAll
    void unLocalConDosEquiposMedidos() throws Exception {
        JsonNode cuenta = registrar("por-dispositivo@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Por Dispositivo SAC","tax_id":"20977777777",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
        local = campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"DIS-1","name":"Tienda Dispositivos","contracted_power_kw":100,
                 "tariff_category":"MT2"}"""));

        // Un unico medidor para los dos equipos: es lo normal, el punto de
        // medida es del local y los equipos cuelgan de el.
        medidor = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"DIS-M"}"""
                .formatted(userId, local)));

        camara = equipo("DIS-FRIO", "Camara de frio", "REFRIGERATION");
        climatizacion = equipo("DIS-AIRE", "Climatizacion", "HVAC");

        // Dos lecturas de cada equipo, para que el resumen tenga que sumarlas y
        // no se limite a copiar la ultima.
        lectura(camara, 40.0, 6000, fueraDePunta());
        lectura(camara, 20.0, 4000, enPunta());
        lectura(climatizacion, 15.0, 3000, fueraDePunta());
        lectura(climatizacion, 10.0, 2500, fueraDePunta());

        // Y una sin device_id: el medidor mide el local, no un aparato, asi que
        // esto es lo habitual y no una rareza.
        lectura(null, 25.0, 1800, fueraDePunta());
    }

    @Test
    @DisplayName("el consumo del periodo se reparte entre los equipos que lo midieron")
    void elConsumoSeRepartePorEquipo() throws Exception {
        JsonNode resumenes = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/device-consumptions/user/" + userId)));

        // Antes esto era una lista vacia: nadie producia el dato.
        assertThat(resumenes).isNotEmpty();

        JsonNode frio = porEquipo(resumenes, camara);
        JsonNode aire = porEquipo(resumenes, climatizacion);

        assertThat(frio.get("total_kwh").asDouble()).isEqualTo(60.0);
        assertThat(frio.get("reading_count").asInt()).isEqualTo(2);
        assertThat(aire.get("total_kwh").asDouble()).isEqualTo(25.0);
        assertThat(aire.get("reading_count").asInt()).isEqualTo(2);

        // El nombre viaja con el resumen: una tarjeta que enumera UUID no se
        // puede leer.
        assertThat(frio.get("device_name").asText()).isEqualTo("Camara de frio");
        assertThat(aire.get("device_name").asText()).isEqualTo("Climatizacion");

        // Pico y media son de la potencia, no de la energia, y se calculan
        // sobre las lecturas del equipo.
        assertThat(frio.get("peak_power_watts").asDouble()).isEqualTo(6000.0);
        assertThat(frio.get("average_power_watts").asDouble()).isEqualTo(5000.0);
        assertThat(frio.get("meter_id").asText()).isEqualTo(medidor);

        // Ordenados de mayor a menor: el primero es donde hay que mirar.
        assertThat(resumenes.get(0).get("total_kwh").asDouble())
                .isGreaterThanOrEqualTo(resumenes.get(1).get("total_kwh").asDouble());
    }

    @Test
    @DisplayName("la energia de punta del equipo se valora con la tarifa del local")
    void elCosteDistingueLaFranja() throws Exception {
        JsonNode resumenes = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/device-consumptions/user/" + userId)));
        JsonNode frio = porEquipo(resumenes, camara);
        JsonNode aire = porEquipo(resumenes, climatizacion);

        JsonNode tarifa = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/energy/tariffs/MT2")));
        double punta = tarifa.get("energy_peak_per_kwh").asDouble();
        double fuera = tarifa.get("energy_off_peak_per_kwh").asDouble();

        // 40 kWh fuera de punta y 20 en punta, cada parte a su precio. Valorar
        // los 60 kWh a un precio plano daria otro numero.
        assertThat(frio.get("cost_estimate_soles").asDouble())
                .isEqualTo(round2(40 * fuera + 20 * punta));
        assertThat(aire.get("cost_estimate_soles").asDouble()).isEqualTo(round2(25 * fuera));
    }

    @Test
    @DisplayName("lo medido sin device_id no se pierde: sale como no asignado")
    void loNoAsignadoNoSePierde() throws Exception {
        JsonNode resumenes = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/device-consumptions/user/" + userId)));

        // La fila sin device_id es el consumo que el medidor no puede atribuir a
        // ningun aparato. Si se omitiera, el reparto no sumaria el consumo del
        // local y la tarjeta mentiria.
        JsonNode sinAsignar = sinEquipo(resumenes);
        assertThat(sinAsignar).isNotNull();
        assertThat(sinAsignar.get("total_kwh").asDouble()).isEqualTo(25.0);
        assertThat(sinAsignar.has("device_name")).isFalse();

        double repartido = 0;
        for (JsonNode resumen : resumenes) {
            repartido += resumen.get("total_kwh").asDouble();
        }
        assertThat(repartido).isEqualTo(110.0);
    }

    @Test
    @DisplayName("el ranking de equipos deja fuera lo no asignado, que no es un equipo")
    void elRankingSoloTieneEquipos() throws Exception {
        JsonNode top = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/device-consumptions/user/" + userId + "/top")));

        assertThat(top).hasSize(2);
        assertThat(sinEquipo(top)).isNull();
        assertThat(top.get(0).get("device_id").asText()).isEqualTo(camara);

        // Y el limite recorta de verdad.
        JsonNode unoSolo = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/device-consumptions/user/" + userId + "/top")
                .param("limit", "1")));
        assertThat(unoSolo).hasSize(1);
    }

    @Test
    @DisplayName("la ventana se puede acortar y deja fuera lo anterior")
    void laVentanaRecorta() throws Exception {
        JsonNode unDia = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/device-consumptions/user/" + userId).param("days", "1")));
        // Todas las lecturas se sembraron hace mas de un dia.
        assertThat(unDia).isEmpty();
    }

    @Test
    @DisplayName("nadie consulta el consumo de otra persona")
    void nadieMiraElConsumoDeOtro() throws Exception {
        JsonNode otra = registrar("por-dispositivo-ajeno@energix.test");
        MvcResult res = mvc.perform(MockMvcRequestBuilders
                .get("/api/v1/device-consumptions/user/" + userId)
                .header("Authorization", "Bearer " + otra.get("token").asText())).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ apoyo

    private static JsonNode porEquipo(JsonNode resumenes, String deviceId) {
        for (JsonNode resumen : resumenes) {
            if (resumen.has("device_id") && resumen.get("device_id").asText().equals(deviceId)) {
                return resumen;
            }
        }
        throw new AssertionError("no hay resumen para el equipo " + deviceId + ": " + resumenes);
    }

    private static JsonNode sinEquipo(JsonNode resumenes) {
        for (JsonNode resumen : resumenes) {
            if (!resumen.has("device_id")) {
                return resumen;
            }
        }
        return null;
    }

    private String equipo(String codigo, String nombre, String tipo) throws Exception {
        return campo(201, "deviceId", post("/api/v1/device-management/devices", """
                {"externalDeviceCode":"%s","userId":"%s","siteId":"%s","deviceName":"%s",
                 "deviceType":"%s","connectionProtocol":"WIFI"}"""
                .formatted(codigo, userId, local, nombre, tipo)));
    }

    /** Una lectura del medidor del local, atribuida o no a un equipo. */
    private void lectura(String deviceId, double kwh, double vatios, Instant cuando)
            throws Exception {
        String equipo = deviceId == null ? "" : "\"device_id\":\"%s\",".formatted(deviceId);
        ejecutar(201, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s",%s"power_watts":%s,"voltage":220,"current":9,
                 "frequency":60,"energy_kwh":%s,"timestamp":"%s"}"""
                .formatted(userId, medidor, equipo, vatios, kwh, cuando)));
    }

    /** Las 20:00 de Peru de hace tres dias: dentro de la franja de punta (18-23). */
    private Instant enPunta() {
        return hace(3).withHour(20).withMinute(0).toInstant(PERU);
    }

    /** Las 10:00 de Peru de hace tres dias. */
    private Instant fueraDePunta() {
        return hace(3).withHour(10).withMinute(0).toInstant(PERU);
    }

    private LocalDateTime hace(int dias) {
        return LocalDateTime.ofInstant(
                Instant.now().minus(Duration.ofDays(dias)).truncatedTo(ChronoUnit.HOURS), PERU);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private JsonNode registrar(String correo) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"%s","password":"Energix2026","role":"STAFF"}"""
                        .formatted(correo))).andReturn();
        assertThat(res.getResponse().getStatus())
                .as(res.getResponse().getContentAsString()).isEqualTo(200);
        return cuerpo(res);
    }

    private MockHttpServletRequestBuilder post(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String campo(int esperado, String nombre, MockHttpServletRequestBuilder peticion)
            throws Exception {
        return cuerpo(ejecutar(esperado, peticion)).get(nombre).asText();
    }

    private MvcResult ejecutar(int esperado, MockHttpServletRequestBuilder peticion)
            throws Exception {
        MvcResult res = mvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
        assertThat(res.getResponse().getStatus())
                .as("respuesta: " + res.getResponse().getContentAsString()).isEqualTo(esperado);
        return res;
    }

    private JsonNode cuerpo(MvcResult res) throws Exception {
        return JSON.readTree(res.getResponse().getContentAsString());
    }
}
