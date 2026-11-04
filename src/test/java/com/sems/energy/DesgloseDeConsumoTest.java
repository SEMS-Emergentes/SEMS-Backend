package com.sems.energy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
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
 * Reparto del consumo por zona (US26) y entre locales (US39).
 *
 * <p>El backlog pedia las dos y ninguna existia: habia medidores por zona, pero
 * nadie sumaba sus lecturas, y la comparacion disponible era entre dispositivos,
 * no entre establecimientos. Son las dos preguntas que justifican medir por
 * zona: donde se gasta dentro de un local y cual de los locales gasta de mas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesgloseDeConsumoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneOffset PERU = ZoneOffset.ofHours(-5);

    @Autowired
    private MockMvc mvc;

    private String token;
    private String userId;
    private String organizacion;
    private String tienda;
    private String kiosco;
    private String zonaFrio;
    private String zonaVentas;

    @BeforeAll
    void unaCadenaConDosLocales() throws Exception {
        JsonNode cuenta = registrar("desglose@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        organizacion = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Cadena Desglose SAC","tax_id":"20911111111",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));

        // Dos locales de tamano muy distinto: es lo que hace que el total por si
        // solo no sirva para compararlos.
        tienda = campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"DES-A","name":"Tienda Centro","contracted_power_kw":100,
                 "tariff_category":"MT2"}"""));
        kiosco = campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"DES-B","name":"Kiosco Norte","contracted_power_kw":20,
                 "tariff_category":"MT2"}"""));

        zonaFrio = campo(201, "zone_id", post("/api/v1/sites/" + tienda + "/zones", """
                {"name":"Camaras de frio","zone_type":"COLD_STORAGE"}"""));
        zonaVentas = campo(201, "zone_id", post("/api/v1/sites/" + tienda + "/zones", """
                {"name":"Sala de ventas","zone_type":"SALES_FLOOR"}"""));

        // 60 kWh en frio, 30 en ventas, 10 en un medidor todavia sin zona. El
        // frio gasta la mitad en hora punta, como pasa de verdad: las camaras
        // no se apagan por la tarde. La sala de ventas, nada.
        medirEnZona("DES-FRIO", zonaFrio, 30.0, 30.0);
        medirEnZona("DES-VENTAS", zonaVentas, 0.0, 30.0);
        medirSinZona("DES-SUELTO", 10.0);

        // El kiosco gasta menos en total pero tiene mucha menos potencia.
        medirEnLocal(kiosco, "DES-KIOSCO", 40.0);
    }

    @Test
    @DisplayName("US26: el consumo del local se reparte entre sus zonas")
    void elConsumoSeRepartePorZona() throws Exception {
        JsonNode desglose = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/sites/" + tienda + "/zone-consumption")));

        assertThat(desglose.get("site_name").asText()).isEqualTo("Tienda Centro");
        assertThat(desglose.get("total_kwh").asDouble()).isEqualTo(100.0);

        JsonNode zonas = desglose.get("zones");
        assertThat(zonas).hasSize(2);
        // Ordenadas por consumo: la primera es donde hay que mirar.
        assertThat(zonas.get(0).get("zone_name").asText()).isEqualTo("Camaras de frio");
        assertThat(zonas.get(0).get("kwh").asDouble()).isEqualTo(60.0);
        assertThat(zonas.get(0).get("share_percent").asDouble()).isEqualTo(60.0);
        assertThat(zonas.get(0).get("meter_count").asInt()).isEqualTo(1);
        assertThat(zonas.get(1).get("zone_name").asText()).isEqualTo("Sala de ventas");
        assertThat(zonas.get(1).get("kwh").asDouble()).isEqualTo(30.0);

        // El medidor sin zona no se esconde: si se omitiera, los porcentajes no
        // sumarian cien y el reparto mentiria.
        JsonNode suelto = desglose.get("unassigned");
        assertThat(suelto.get("kwh").asDouble()).isEqualTo(10.0);
        assertThat(suelto.get("meter_count").asInt()).isEqualTo(1);
        assertThat(zonas.get(0).get("share_percent").asDouble()
                + zonas.get(1).get("share_percent").asDouble()
                + suelto.get("share_percent").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("US26: la energia de punta se cobra mas cara que la de fuera de punta")
    void elCosteDistingueLaFranja() throws Exception {
        JsonNode desglose = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/sites/" + tienda + "/zone-consumption")));
        JsonNode frio = desglose.get("zones").get(0);
        JsonNode ventas = desglose.get("zones").get(1);

        // Frio consume el doble que ventas (60 contra 30) y cuesta mas del
        // doble, porque la mitad de su consumo cae en hora punta y el de
        // ventas no. Repartir por kWh sin mirar la franja escondia esto.
        double costeFrio = frio.get("estimated_energy_cost").asDouble();
        double costeVentas = ventas.get("estimated_energy_cost").asDouble();
        assertThat(costeFrio).isGreaterThan(costeVentas * 2);
        assertThat(desglose.get("currency").asText()).isEqualTo("PEN");
    }

    @Test
    @DisplayName("US26: un local sin zonas devuelve el consumo entero como no asignado")
    void localSinZonas() throws Exception {
        JsonNode desglose = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/sites/" + kiosco + "/zone-consumption")));
        assertThat(desglose.get("zones")).isEmpty();
        assertThat(desglose.get("unassigned").get("kwh").asDouble()).isEqualTo(40.0);
        assertThat(desglose.get("unassigned").get("share_percent").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("US39: los locales se comparan por consumo y por kWh por kW contratado")
    void losLocalesSeComparan() throws Exception {
        JsonNode comparacion = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/organizations/" + organizacion + "/site-consumption")));

        assertThat(comparacion.get("total_kwh").asDouble()).isEqualTo(140.0);
        JsonNode locales = comparacion.get("sites");
        assertThat(locales).hasSize(2);

        JsonNode mayor = locales.get(0);
        JsonNode menor = locales.get(1);
        assertThat(mayor.get("site_name").asText()).isEqualTo("Tienda Centro");
        assertThat(mayor.get("kwh").asDouble()).isEqualTo(100.0);
        assertThat(menor.get("kwh").asDouble()).isEqualTo(40.0);

        // Y aqui esta el motivo de dar tambien el consumo por kW contratado: la
        // tienda gasta mas de dos veces y media lo del kiosco, pero por unidad
        // de potencia contratada el kiosco gasta el doble. Comparar totales
        // entre locales de distinto tamano no dice nada.
        assertThat(mayor.get("kwh_per_contracted_kw").asDouble()).isEqualTo(1.0);
        assertThat(menor.get("kwh_per_contracted_kw").asDouble()).isEqualTo(2.0);
        assertThat(mayor.get("share_percent").asDouble()).isEqualTo(71.4);
    }

    @Test
    @DisplayName("la ventana se puede acortar y deja fuera lo anterior")
    void laVentanaRecorta() throws Exception {
        JsonNode unDia = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/sites/" + tienda + "/zone-consumption").param("days", "1")));
        // Todas las lecturas se sembraron hace mas de un dia.
        assertThat(unDia.get("total_kwh").asDouble()).isEqualTo(0.0);
        assertThat(unDia.get("days").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("un local ajeno no se consulta, y uno inexistente es 404")
    void nadieMiraElLocalDeOtro() throws Exception {
        JsonNode otra = registrar("desglose-ajeno@energix.test");
        MvcResult res = mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/sites/" + tienda + "/zone-consumption")
                        .header("Authorization", "Bearer " + otra.get("token").asText()))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(403);

        ejecutar(404, MockMvcRequestBuilders
                .get("/api/v1/sites/11111111-2222-3333-4444-555555555555/zone-consumption"));
    }

    // ------------------------------------------------------------------ apoyo

    /** Siembra un medidor en una zona con consumo en punta y fuera de punta. */
    private void medirEnZona(String serie, String zona, double kwhPunta, double kwhFueraDePunta)
            throws Exception {
        String medidor = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"%s","meter_serial":"%s"}"""
                .formatted(userId, tienda, zona, serie)));
        if (kwhPunta > 0) {
            lectura(medidor, kwhPunta, enPunta());
        }
        if (kwhFueraDePunta > 0) {
            lectura(medidor, kwhFueraDePunta, fueraDePunta());
        }
    }

    private void medirSinZona(String serie, double kwh) throws Exception {
        String medidor = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"%s"}"""
                .formatted(userId, tienda, serie)));
        lectura(medidor, kwh, fueraDePunta());
    }

    private void medirEnLocal(String local, String serie, double kwh) throws Exception {
        String medidor = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"%s"}"""
                .formatted(userId, local, serie)));
        lectura(medidor, kwh, fueraDePunta());
    }

    private void lectura(String medidor, double kwh, Instant cuando) throws Exception {
        ejecutar(201, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","power_watts":1500,"voltage":220,"current":7,
                 "frequency":60,"energy_kwh":%s,"timestamp":"%s"}"""
                .formatted(userId, medidor, kwh, cuando)));
    }

    /** Las 20:00 de Peru de hace tres dias: dentro de la franja de punta (18-23). */
    private Instant enPunta() {
        return hace(3).atZone(PERU).withHour(20).withMinute(0).toInstant();
    }

    /** Las 10:00 de Peru de hace tres dias. */
    private Instant fueraDePunta() {
        return hace(3).atZone(PERU).withHour(10).withMinute(0).toInstant();
    }

    private java.time.LocalDateTime hace(int dias) {
        return java.time.LocalDateTime.ofInstant(
                Instant.now().minus(Duration.ofDays(dias)).truncatedTo(ChronoUnit.HOURS), PERU);
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
