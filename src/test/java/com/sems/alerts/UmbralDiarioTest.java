package com.sems.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
 * Umbrales de consumo por dia.
 *
 * <p>La pantalla define el umbral en kWh por dia, pero se comparaba con la
 * energia de cada lectura suelta: con lecturas horarias de 1 kWh, un limite de
 * 5 kWh/dia no avisaba nunca aunque el equipo consumiera el doble. Estas
 * pruebas fallan contra ese codigo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UmbralDiarioTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneOffset PERU = ZoneOffset.ofHours(-5);

    @Autowired
    private MockMvc mvc;

    private String token;
    private String userId;
    private String local;
    private String medidor;

    @BeforeAll
    void unLocalConMedidor() throws Exception {
        MvcResult reg = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"umbral-diario@energix.test","password":"Energix2026","role":"STAFF"}"""))
                .andReturn();
        JsonNode cuenta = cuerpo(reg);
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        String org = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Umbral Diario SAC","tax_id":"20944444444",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
        local = campo(201, "site_id", post("/api/v1/organizations/" + org + "/sites", """
                {"site_code":"UMB-1","name":"Tienda Umbral","contracted_power_kw":40,
                 "tariff_category":"BT5B"}"""));
        medidor = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"UMB-M"}""".formatted(userId, local)));
    }

    @Test
    @DisplayName("avisa cuando el consumo del dia pasa el limite, aunque ninguna lectura sola lo pase")
    void sumaElDia() throws Exception {
        String camara = equipo("UMB-CAM", "Camara de frio");
        umbral(camara, 5);

        // Cinco lecturas de 1 kWh: el dia llega a 5, que no pasa de 5.
        for (int h = 9; h < 14; h++) {
            lectura(camara, ayerA(h), 1.0);
        }
        Thread.sleep(800);
        assertThat(avisos(camara)).isEmpty();

        // La sexta deja el dia en 6 kWh.
        lectura(camara, ayerA(14), 1.0);
        assertThat(esperarAvisos(camara, 1)).hasSize(1);

        // Seguir consumiendo ese dia no repite el aviso.
        lectura(camara, ayerA(15), 1.0);
        lectura(camara, ayerA(16), 1.0);
        Thread.sleep(800);
        assertThat(avisos(camara)).hasSize(1);
    }

    @Test
    @DisplayName("un lote de lecturas del mismo dia levanta un solo aviso")
    void unLoteUnAviso() throws Exception {
        String vitrina = equipo("UMB-VIT", "Vitrina");
        umbral(vitrina, 3);
        String clave = cuerpo(ejecutar(201, post("/api/v1/energy-meters/" + medidor + "/credential", "{}")))
                .get("api_key").asText();

        // Diez lecturas en un solo envio: se evaluan en paralelo.
        List<String> lote = new ArrayList<>();
        for (int h = 8; h < 18; h++) {
            lote.add("""
                    {"energy_kwh":1.0,"power_watts":1000,"timestamp":"%s","device_id":"%s"}"""
                    .formatted(ayerA(h), vitrina));
        }
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/ingest/readings")
                .header("X-Meter-Key", clave).contentType(MediaType.APPLICATION_JSON)
                .content("{\"readings\":[" + String.join(",", lote) + "]}")).andReturn();
        assertThat(res.getResponse().getStatus()).as(res.getResponse().getContentAsString()).isEqualTo(200);

        assertThat(esperarAvisos(vitrina, 1)).hasSize(1);
        Thread.sleep(800);
        assertThat(avisos(vitrina)).hasSize(1);
    }

    // ------------------------------------------------------------------ apoyo

    private static Instant ayerA(int hora) {
        return LocalDate.now(PERU).minusDays(1).atTime(hora, 0).toInstant(PERU);
    }

    private List<JsonNode> esperarAvisos(String deviceId, int cuantos) throws Exception {
        List<JsonNode> hallados = avisos(deviceId);
        for (int i = 0; i < 50 && hallados.size() < cuantos; i++) {
            Thread.sleep(100);
            hallados = avisos(deviceId);
        }
        return hallados;
    }

    private List<JsonNode> avisos(String deviceId) throws Exception {
        List<JsonNode> hallados = new ArrayList<>();
        for (JsonNode a : cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/users/" + userId + "/alerts")))) {
            if ("threshold_exceeded".equals(a.get("alert_type").asText())
                    && deviceId.equals(a.get("device_id").asText())) {
                hallados.add(a);
            }
        }
        return hallados;
    }

    private String equipo(String codigo, String nombre) throws Exception {
        return campo(201, "deviceId", post("/api/v1/device-management/devices", """
                {"externalDeviceCode":"%s","userId":"%s","siteId":"%s","deviceName":"%s",
                 "deviceType":"REFRIGERATOR","connectionProtocol":"WIFI"}"""
                .formatted(codigo, userId, local, nombre)));
    }

    private void umbral(String deviceId, double kwhPorDia) throws Exception {
        ejecutar(201, post("/api/v1/thresholds", """
                {"user_id":"%s","device_id":"%s","threshold_name":"Consumo alto",
                 "metric":"consumption_kwh","operator":">","threshold_value":%s}"""
                .formatted(userId, deviceId, kwhPorDia)));
    }

    private void lectura(String deviceId, Instant cuando, double kwh) throws Exception {
        ejecutar(201, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","device_id":"%s","power_watts":1000,
                 "voltage":220,"current":4.5,"frequency":60,"energy_kwh":%s,"timestamp":"%s"}"""
                .formatted(userId, medidor, deviceId, kwh, cuando)));
    }

    private MockHttpServletRequestBuilder post(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private String campo(int esperado, String nombre, MockHttpServletRequestBuilder peticion)
            throws Exception {
        return cuerpo(ejecutar(esperado, peticion)).get(nombre).asText();
    }

    private MvcResult ejecutar(int esperado, MockHttpServletRequestBuilder peticion) throws Exception {
        MvcResult res = mvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
        assertThat(res.getResponse().getStatus())
                .as("respuesta: " + res.getResponse().getContentAsString()).isEqualTo(esperado);
        return res;
    }

    private JsonNode cuerpo(MvcResult res) throws Exception {
        return JSON.readTree(res.getResponse().getContentAsString());
    }
}
