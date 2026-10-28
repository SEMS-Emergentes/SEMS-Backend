package com.sems.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sems.alerts.application.AlertCommandService;
import com.sems.analytics.application.InsightEngine;
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
 * Lo que el sistema deduce por su cuenta del consumo medido.
 *
 * <p>Recomendaciones, anomalias y proyeccion de factura tenian pantalla,
 * almacenamiento y endpoint de alta, pero ningun productor: esperaban a que
 * alguien las creara desde fuera, de modo que las tres salian vacias por mucho
 * que el cliente midiera. Lo mismo pasaba con el aviso de demanda, que solo se
 * evaluaba si una persona escribia la cifra a mano.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HallazgosYPrecioTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneOffset PERU = ZoneOffset.ofHours(-5);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private InsightEngine engine;
    @Autowired
    private AlertCommandService alertas;

    private String token;
    private String userId;
    private String local;
    private String zonaFrio;
    private String medidorFrio;

    @BeforeAll
    void unLocalConUnMesDeConsumo() throws Exception {
        JsonNode cuenta = registrar("hallazgos@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Hallazgos SAC","tax_id":"20811111111",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
        local = campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"HAL-1","name":"Tienda Hallazgos","contracted_power_kw":100,
                 "tariff_category":"MT2"}"""));
        zonaFrio = campo(201, "zone_id", post("/api/v1/sites/" + local + "/zones", """
                {"name":"Camaras de frio","zone_type":"COLD_STORAGE"}"""));
        medidorFrio = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"%s","meter_serial":"HAL-F"}"""
                .formatted(userId, local, zonaFrio)));
    }

    @Test
    @DisplayName("cada lectura trae su coste con la tarifa del local y su franja")
    void laLecturaTraeSuCoste() throws Exception {
        JsonNode enPunta = cuerpo(ejecutar(201, lectura(medidorFrio, 10, enPuntaDe(2), 3000)));
        JsonNode fuera = cuerpo(ejecutar(201, lectura(medidorFrio, 10, fueraDePuntaDe(2), 3000)));

        // Mismos kWh, distinta hora, distinto precio: eso es un suministro
        // comercial. Antes el cliente multiplicaba por un unico precio plano.
        double costePunta = enPunta.get("estimated_cost").asDouble();
        double costeFuera = fuera.get("estimated_cost").asDouble();
        assertThat(costePunta).isGreaterThan(costeFuera);

        // Y cuadra con la tarifa publicada del local, no con un numero aparte.
        JsonNode tarifa = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/energy/tariffs/MT2")));
        assertThat(costePunta)
                .isEqualTo(round2(tarifa.get("energy_peak_per_kwh").asDouble() * 10));
        assertThat(costeFuera)
                .isEqualTo(round2(tarifa.get("energy_off_peak_per_kwh").asDouble() * 10));
    }

    @Test
    @DisplayName("la hora punta ya no dice que los domingos quedan fuera")
    void laHoraPuntaNoMiente() throws Exception {
        JsonNode tarifa = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/energy/tariffs/MT2")));
        // Decia "18:00-23:00 lun-sab". La exclusion de domingos no es
        // automatica: la pide el cliente a su distribuidora.
        assertThat(tarifa.get("peak_hours").asText()).isEqualTo("18:00-23:00");
        assertThat(tarifa.get("peak_every_day").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("el error de validacion nombra el campo tal como viaja en el JSON")
    void elErrorNombraElCampoDelContrato() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/thresholds", """
                {"user_id":"%s"}""".formatted(userId))
                .header("Authorization", "Bearer " + token)).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        String mensaje = res.getResponse().getContentAsString();
        // Antes contestaba "thresholdName is required", que manda a arreglar un
        // campo que la API no acepta.
        assertThat(mensaje).contains("threshold_name");
        assertThat(mensaje).doesNotContain("thresholdName");
    }

    @Test
    @DisplayName("una lectura alta levanta el aviso de demanda sin que nadie lo pida")
    void laDemandaSeEvaluaSola() throws Exception {
        ejecutar(201, post("/api/v1/demand-rules", """
                {"site_id":"%s","user_id":"%s","rule_name":"Aviso 85%%","warning_percent":85}"""
                .formatted(local, userId)));

        // 92 kW sobre 100 contratados: dentro del contrato pero en el margen.
        var primera = alertas.evaluarDemandaAutomatica(java.util.UUID.fromString(local), 92);
        assertThat(primera).hasSize(1);
        assertThat(primera.get(0).getSeverity()).isEqualTo("WARNING");

        // La siguiente lectura sigue alta: el aviso no se repite, porque una
        // bandeja con cuarenta copias del mismo aviso no se lee.
        assertThat(alertas.evaluarDemandaAutomatica(java.util.UUID.fromString(local), 93))
                .isEmpty();

        // Superar lo contratado si es una situacion nueva y vuelve a avisar.
        var exceso = alertas.evaluarDemandaAutomatica(java.util.UUID.fromString(local), 120);
        assertThat(exceso).hasSize(1);
        assertThat(exceso.get(0).getSeverity()).isEqualTo("CRITICAL");

        // Y al volver a la normalidad se olvida, para que el proximo pico avise
        // enseguida y no media hora despues.
        assertThat(alertas.evaluarDemandaAutomatica(java.util.UUID.fromString(local), 40))
                .isEmpty();
        assertThat(alertas.evaluarDemandaAutomatica(java.util.UUID.fromString(local), 92))
                .hasSize(1);
    }

    @Test
    @DisplayName("del consumo medido salen anomalias, recomendaciones y proyeccion")
    void elSistemaDeduceSolo() throws Exception {
        JsonNode cuenta = registrar("hallazgos-2@energix.test");
        String suToken = cuenta.get("token").asText();
        String suId = cuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", suToken,
                post("/api/v1/organizations", """
                {"legal_name":"Deduce SAC","tax_id":"20822222222",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(suId)));
        String suLocal = campo(201, "site_id", suToken,
                post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"DED-1","name":"Tienda Deduce","contracted_power_kw":60,
                 "tariff_category":"MT2"}"""));
        String suZona = campo(201, "zone_id", suToken, post("/api/v1/sites/" + suLocal + "/zones", """
                {"name":"Camaras de frio","zone_type":"COLD_STORAGE"}"""));
        String suMedidor = campo(201, "id", suToken, post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"%s","meter_serial":"DED-F"}"""
                .formatted(suId, suLocal, suZona)));

        // Doce dias estables y uno que se dispara al final.
        for (int dia = 13; dia >= 2; dia--) {
            ejecutarCon(suToken, 201, lectura(suMedidor, 20, enPuntaDe(dia), 50000));
            ejecutarCon(suToken, 201, lectura(suMedidor, 20, fueraDePuntaDe(dia), 50000));
        }
        // El pico va en el ultimo dia cerrado: el dia en curso no se analiza.
        ejecutarCon(suToken, 201, lectura(suMedidor, 95, fueraDePuntaDe(1), 55000));

        engine.recalcular(suId);

        // Anomalia: el ultimo dia cerrado se sale de lo que ese medidor viene
        // haciendo. Se consulta por la API, que es como lo ve el cliente.
        JsonNode anomalias = cuerpo(ejecutarCon(suToken, 200, MockMvcRequestBuilders
                .get("/api/v1/analytics/anomalies/user/" + suId)));
        assertThat(anomalias).hasSize(1);
        assertThat(anomalias.get(0).get("description").asText()).contains("por encima");

        // Recomendaciones: la zona se lleva todo el local, la mitad del consumo
        // cae en punta y el pico roza lo contratado.
        JsonNode recomendaciones = cuerpo(ejecutarCon(suToken, 200, MockMvcRequestBuilders
                .get("/api/v1/analytics/recommendations/user/" + suId)));
        var tipos = new java.util.ArrayList<String>();
        recomendaciones.forEach(r -> tipos.add(r.get("recommendation_type").asText()));
        assertThat(tipos).contains("zone_concentration", "peak_shift", "demand_peak");
        recomendaciones.forEach(r ->
                assertThat(r.get("estimated_saving_amount").asDouble()).isGreaterThan(0));

        // Proyeccion: la pantalla la leia y nadie la escribia nunca.
        JsonNode proyecciones = cuerpo(ejecutarCon(suToken, 200, MockMvcRequestBuilders
                .get("/api/v1/analytics/bill-predictions/user/" + suId)));
        assertThat(proyecciones).isNotEmpty();
        assertThat(proyecciones.get(0).get("estimated_amount").asDouble()).isGreaterThan(0);

        // Y no se duplican: recalcular el mismo dia no anade nada.
        engine.recalcular(suId);
        JsonNode otraVez = cuerpo(ejecutarCon(suToken, 200, MockMvcRequestBuilders
                .get("/api/v1/analytics/recommendations/user/" + suId)));
        assertThat(otraVez).hasSize(recomendaciones.size());
    }

    // ------------------------------------------------------------------ apoyo

    private MockHttpServletRequestBuilder lectura(String medidor, double kwh, Instant cuando,
                                                  double vatios) {
        return post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","power_watts":%s,"voltage":220,"current":9,
                 "frequency":60,"energy_kwh":%s,"timestamp":"%s"}"""
                .formatted(userIdDe(medidor), medidor, vatios, kwh, cuando));
    }

    /** Las lecturas de este test son siempre del usuario que creo el medidor. */
    private String userIdDe(String medidor) {
        return medidor.equals(medidorFrio) ? userId : userIdSecundario;
    }

    private String userIdSecundario;

    private Instant enPuntaDe(int diasAtras) {
        return hace(diasAtras).withHour(20).toInstant(PERU);
    }

    private Instant fueraDePuntaDe(int diasAtras) {
        return hace(diasAtras).withHour(10).toInstant(PERU);
    }

    private LocalDateTime hace(int dias) {
        return LocalDateTime.ofInstant(Instant.now(), PERU)
                .minusDays(dias).truncatedTo(ChronoUnit.HOURS).withMinute(0);
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
        JsonNode cuenta = cuerpo(res);
        userIdSecundario = cuenta.get("userId").asText();
        return cuenta;
    }

    private MockHttpServletRequestBuilder post(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String campo(int esperado, String nombre, MockHttpServletRequestBuilder peticion)
            throws Exception {
        return cuerpo(ejecutar(esperado, peticion)).get(nombre).asText();
    }

    private String campo(int esperado, String nombre, String elToken,
                         MockHttpServletRequestBuilder peticion) throws Exception {
        return cuerpo(ejecutarCon(elToken, esperado, peticion)).get(nombre).asText();
    }

    private MvcResult ejecutar(int esperado, MockHttpServletRequestBuilder peticion)
            throws Exception {
        return ejecutarCon(token, esperado, peticion);
    }

    private MvcResult ejecutarCon(String elToken, int esperado,
                                  MockHttpServletRequestBuilder peticion) throws Exception {
        MvcResult res = mvc.perform(peticion.header("Authorization", "Bearer " + elToken))
                .andReturn();
        assertThat(res.getResponse().getStatus())
                .as("respuesta: " + res.getResponse().getContentAsString()).isEqualTo(esperado);
        return res;
    }

    private JsonNode cuerpo(MvcResult res) throws Exception {
        return JSON.readTree(res.getResponse().getContentAsString());
    }
}
