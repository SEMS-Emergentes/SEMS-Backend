package com.sems.organizations;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * El plan contratado limita cuantos locales se pueden abrir (US42).
 *
 * <p>El cupo existia solo como texto en la ficha del plan: nadie lo leia, de
 * modo que un cliente de Starter podia abrir locales sin limite. Era un hueco
 * de ingreso, no solo funcional, porque los planes se miden justamente en
 * locales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CupoDeLocalesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private String token;
    private String userId;
    private String organizacion;
    private String planStarter;

    @BeforeAll
    void prepararCuentaYOrganizacion() throws Exception {
        JsonNode cuenta = registrar("cupo@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        organizacion = cuerpo(ejecutar(201, enviar("/api/v1/organizations", """
                {"legal_name":"Cadena Cupo SAC","tax_id":"20522222222",
                 "business_type":"CONVENIENCE_STORE","owner_user_id":"%s"}"""
                .formatted(userId)))).get("organization_id").asText();

        JsonNode planes = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/subscription-plans")));
        for (JsonNode plan : planes) {
            if ("Starter".equals(plan.get("Name").asText())) {
                planStarter = plan.get("PlanID").asText();
            }
        }
        assertThat(planStarter).as("el seeder tiene que haber cargado Starter").isNotNull();
    }

    @Test
    @DisplayName("sin suscripcion no se aplica cupo")
    void sinSuscripcionNoHayCupo() throws Exception {
        // Un limite que no se conoce no se inventa: bloquear por desconocimiento
        // dejaria sin operar a quien aun no ha contratado nada.
        crearLocal("LIBRE-1", 201);
        crearLocal("LIBRE-2", 201);
    }

    @Test
    @DisplayName("con Starter, que incluye un local, el segundo se rechaza")
    void starterIncluyeUnLocal() throws Exception {
        JsonNode cuenta = registrar("cupo-starter@energix.test");
        String tokenB = cuenta.get("token").asText();
        String userB = cuenta.get("userId").asText();

        String orgB = cuerpo(ejecutarCon(tokenB, 201, enviar("/api/v1/organizations", """
                {"legal_name":"Tienda Unica SAC","tax_id":"20533333333",
                 "business_type":"CONVENIENCE_STORE","owner_user_id":"%s"}"""
                .formatted(userB)))).get("organization_id").asText();

        ejecutarCon(tokenB, 201, enviar("/api/v1/subscriptions", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(userB, planStarter)));

        String primero = cuerpo(ejecutarCon(tokenB, 201,
                enviar("/api/v1/organizations/" + orgB + "/sites", """
                {"site_code":"UNO","name":"Local uno","contracted_power_kw":40,
                 "tariff_category":"BT5B"}"""))).get("site_id").asText();

        // El segundo no entra, y el mensaje dice que plan es y cuanto incluye,
        // que es lo que US42 pide: saber cuando hay que ampliarlo.
        MvcResult rechazo = ejecutarCon(tokenB, 409,
                enviar("/api/v1/organizations/" + orgB + "/sites", """
                {"site_code":"DOS","name":"Local dos","contracted_power_kw":40,
                 "tariff_category":"BT5B"}"""));
        assertThat(rechazo.getResponse().getContentAsString())
                .contains("Starter").contains("1 site(s)");

        // Al archivar el primero se libera el cupo: un local archivado no ocupa,
        // que es justo lo que uno espera al archivarlo.
        ejecutarCon(tokenB, 204, MockMvcRequestBuilders.delete("/api/v1/sites/" + primero));
        ejecutarCon(tokenB, 201, enviar("/api/v1/organizations/" + orgB + "/sites", """
                {"site_code":"TRES","name":"Local tres","contracted_power_kw":40,
                 "tariff_category":"BT5B"}"""));
    }

    // ------------------------------------------------------------------ apoyo

    private void crearLocal(String codigo, int esperado) throws Exception {
        ejecutar(esperado, enviar("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"%s","name":"Local %s","contracted_power_kw":50,
                 "tariff_category":"BT5B"}""".formatted(codigo, codigo)));
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

    private MockHttpServletRequestBuilder enviar(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
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
