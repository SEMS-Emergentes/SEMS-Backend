package com.sems.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sems.alerts.application.AlertCommandService;
import java.util.UUID;
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
 * El mismo pico de demanda no se avisa dos veces por venir de dos sitios.
 *
 * <p>La ventana de silencio de la regla se aplicaba cuando la evaluacion la
 * disparaba una lectura, pero no cuando alguien pulsaba el boton de evaluar:
 * con el mismo numero, el endpoint manual levantaba una copia del aviso que la
 * lectura acababa de generar sola. Para quien lee la bandeja esas dos rutas son
 * la misma situacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvisoDeDemandaDuplicadoTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;
    /** La misma puerta por la que entra la evaluacion al llegar una lectura. */
    @Autowired
    private AlertCommandService alertas;

    private String token;
    private String userId;
    private String local;

    @BeforeAll
    void unLocalConAvisoAl85() throws Exception {
        JsonNode cuenta = registrar("demanda-duplicada@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Demanda SAC","tax_id":"20988888888",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
        local = campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"DEM-D","name":"Tienda Demanda","contracted_power_kw":120,
                 "tariff_category":"MT2"}"""));
        ejecutar(201, post("/api/v1/demand-rules", """
                {"site_id":"%s","user_id":"%s","rule_name":"Aviso 85%%","warning_percent":85}"""
                .formatted(local, userId)));
    }

    @Test
    @DisplayName("la evaluacion manual no duplica el aviso que la lectura acaba de levantar")
    void elBotonNoDuplicaElAviso() throws Exception {
        UUID site = UUID.fromString(local);

        // 104 kW sobre 120 contratados: pasa del umbral de aviso (102) sin
        // superar el contrato. La ruta de las lecturas levanta un WARNING.
        var automatica = alertas.evaluateDemand(site, 104);
        assertThat(automatica).hasSize(1);
        assertThat(automatica.get(0).getSeverity()).isEqualTo("WARNING");

        // Y ahora el boton de evaluar, con la misma demanda y dentro de la media
        // hora de silencio. Antes devolvia una segunda alerta identica.
        assertThat(avisosDeLaEvaluacionManual(104))
                .as("el aviso ya estaba levantado: la ventana de silencio es de la regla, "
                        + "no de la ruta")
                .isEmpty();

        // Superar lo contratado si es una situacion nueva: el nivel cambia y
        // vuelve a avisar aunque la llamada sea manual.
        JsonNode exceso = avisosDeLaEvaluacionManual(132);
        assertThat(exceso).hasSize(1);
        assertThat(exceso.get(0).get("severity").asText()).isEqualTo("CRITICAL");

        // Repetir el exceso tampoco duplica, venga de donde venga.
        assertThat(avisosDeLaEvaluacionManual(132)).isEmpty();
        assertThat(alertas.evaluateDemand(site, 132)).isEmpty();

        // En la bandeja quedan dos avisos y no cuatro: un WARNING y un CRITICAL.
        JsonNode bandeja = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/users/" + userId + "/alerts")));
        assertThat(bandeja).hasSize(2);
    }

    @Test
    @DisplayName("al volver a la normalidad el silencio se levanta tambien por la ruta manual")
    void laNormalidadOlvidaElAviso() throws Exception {
        JsonNode otraCuenta = registrar("demanda-normalidad@energix.test");
        String suToken = otraCuenta.get("token").asText();
        String suId = otraCuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", suToken,
                post("/api/v1/organizations", """
                {"legal_name":"Normalidad SAC","tax_id":"20999999999",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(suId)));
        String suLocal = campo(201, "site_id", suToken,
                post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"DEM-N","name":"Tienda Normalidad","contracted_power_kw":120,
                 "tariff_category":"MT2"}"""));
        ejecutarCon(suToken, 201, post("/api/v1/demand-rules", """
                {"site_id":"%s","user_id":"%s","rule_name":"Aviso 85%%","warning_percent":85}"""
                .formatted(suLocal, suId)));

        assertThat(avisos(suToken, suLocal, 110)).hasSize(1);
        assertThat(avisos(suToken, suLocal, 110)).isEmpty();

        // 90 kW esta por debajo del umbral: la regla olvida el aviso anterior
        // para que el proximo pico avise enseguida y no media hora despues.
        assertThat(avisos(suToken, suLocal, 90)).isEmpty();
        assertThat(avisos(suToken, suLocal, 110)).hasSize(1);
    }

    // ------------------------------------------------------------------ apoyo

    private JsonNode avisosDeLaEvaluacionManual(double demandaKw) throws Exception {
        return avisos(token, local, demandaKw);
    }

    private JsonNode avisos(String elToken, String elLocal, double demandaKw) throws Exception {
        return cuerpo(ejecutarCon(elToken, 200, post(
                "/api/v1/sites/" + elLocal + "/demand-evaluations",
                "{\"demand_kw\":%s}".formatted(demandaKw))));
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
