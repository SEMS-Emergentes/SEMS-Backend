package com.sems.subscriptions;

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
 * Compra de planes con el procesador simulado.
 *
 * <p>Antes un plan de pago se activaba con un alta o un cambio de plan sin
 * ningun cobro, y el importe que se cobraba lo ponia el navegador. Estas
 * pruebas fallan contra ese codigo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompraDePlanTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String APROBADA = "4242 4242 4242 4242";
    private static final String RECHAZADA = "4000 0000 0000 0002";

    @Autowired
    private MockMvc mvc;

    private String starter;
    private String business;
    private String enterprise;

    @BeforeAll
    void planes() throws Exception {
        String token = registrar("compra-catalogo@energix.test").get("token").asText();
        for (JsonNode plan : cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/subscription-plans")))) {
            switch (plan.get("Name").asText()) {
                case "Starter" -> starter = plan.get("PlanID").asText();
                case "Business" -> business = plan.get("PlanID").asText();
                case "Enterprise" -> enterprise = plan.get("PlanID").asText();
                default -> { }
            }
        }
    }

    @Test
    @DisplayName("un plan de pago no se activa con el alta ni con el cambio directo")
    void sinCobroNoHayPlanDePago() throws Exception {
        var cuenta = registrar("compra-atajo@energix.test");
        String token = cuenta.get("token").asText();
        String usuario = cuenta.get("userId").asText();

        ejecutar(token, 409, post("/api/v1/subscriptions", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(usuario, business)));

        String sub = cuerpo(ejecutar(token, 201, post("/api/v1/subscriptions", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(usuario, starter))))
                .get("SubscriptionID").asText();
        ejecutar(token, 409, MockMvcRequestBuilders
                .patch("/api/v1/subscriptions/" + sub + "/change-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"new_plan_id":"%s"}""".formatted(enterprise)));

        assertThat(planActual(token, usuario)).isEqualTo(starter);
    }

    @Test
    @DisplayName("con tarjeta aprobada se cobra el precio del plan, se emite comprobante y se activa")
    void compraAprobada() throws Exception {
        var cuenta = registrar("compra-ok@energix.test");
        String token = cuenta.get("token").asText();
        String usuario = cuenta.get("userId").asText();

        // Un "amount" en la peticion no cuenta: el importe lo fija el plan.
        JsonNode compra = cuerpo(ejecutar(token, 201, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s","card_number":"%s","exp_month":12,
                 "exp_year":2099,"cvc":"123","amount":0.01}"""
                .formatted(usuario, business, APROBADA))));

        assertThat(compra.get("Subscription").get("PlanID").asText()).isEqualTo(business);
        assertThat(compra.get("Payment").get("Status").asText()).isEqualTo("processed");
        assertThat(compra.get("Payment").get("Amount").asDouble()).isEqualTo(149.0);
        assertThat(compra.get("Payment").get("InvoiceNumber").asText()).startsWith("INV-");
        assertThat(planActual(token, usuario)).isEqualTo(business);

        JsonNode pagos = cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/payments/user/" + usuario)));
        assertThat(pagos).hasSize(1);
        // Del numero solo quedan la marca y los ultimos cuatro digitos.
        assertThat(pagos.get(0).get("payment_method").asText())
                .contains("4242").doesNotContain("4242424242424242");

        // Comprar otra vez el plan que ya tiene seria cobrarle dos veces.
        ejecutar(token, 409, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s","card_number":"%s","exp_month":12,
                 "exp_year":2099,"cvc":"123"}""".formatted(usuario, business, APROBADA)));
    }

    @Test
    @DisplayName("con tarjeta rechazada responde 402, el plan no cambia y el intento queda registrado")
    void compraRechazada() throws Exception {
        var cuenta = registrar("compra-rechazo@energix.test");
        String token = cuenta.get("token").asText();
        String usuario = cuenta.get("userId").asText();
        ejecutar(token, 201, post("/api/v1/subscriptions", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(usuario, starter)));

        MvcResult rechazo = ejecutar(token, 402, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s","card_number":"%s","exp_month":12,
                 "exp_year":2099,"cvc":"123"}""".formatted(usuario, enterprise, RECHAZADA)));
        assertThat(cuerpo(rechazo).get("code").asText()).isEqualTo("PAYMENT_DECLINED");

        assertThat(planActual(token, usuario)).isEqualTo(starter);
        JsonNode pagos = cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/payments/user/" + usuario)));
        assertThat(pagos).hasSize(1);
        assertThat(pagos.get(0).get("status").asText()).isEqualTo("failed");
    }

    @Test
    @DisplayName("una tarjeta mal formada o vencida se rechaza antes de cobrar")
    void tarjetaInvalida() throws Exception {
        var cuenta = registrar("compra-invalida@energix.test");
        String token = cuenta.get("token").asText();
        String usuario = cuenta.get("userId").asText();

        // Falla el digito de control.
        ejecutar(token, 400, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s","card_number":"4242 4242 4242 4241",
                 "exp_month":12,"exp_year":2099,"cvc":"123"}""".formatted(usuario, business)));
        // Vencida.
        ejecutar(token, 400, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s","card_number":"%s",
                 "exp_month":1,"exp_year":2020,"cvc":"123"}"""
                .formatted(usuario, business, APROBADA)));
        // Plan de pago sin tarjeta.
        ejecutar(token, 400, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(usuario, business)));

        assertThat(cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/payments/user/" + usuario)))).isEmpty();
    }

    @Test
    @DisplayName("el plan gratuito se contrata sin tarjeta y sin cobro")
    void planGratuito() throws Exception {
        var cuenta = registrar("compra-gratis@energix.test");
        String token = cuenta.get("token").asText();
        String usuario = cuenta.get("userId").asText();

        JsonNode compra = cuerpo(ejecutar(token, 201, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(usuario, starter))));
        assertThat(compra.get("Subscription").get("PlanID").asText()).isEqualTo(starter);
        assertThat(compra.has("Payment")).isFalse();
    }

    @Test
    @DisplayName("nadie compra un plan a nombre de otro")
    void soloParaUnoMismo() throws Exception {
        String ajeno = registrar("compra-ajeno@energix.test").get("token").asText();
        String victima = registrar("compra-victima@energix.test").get("userId").asText();
        ejecutar(ajeno, 403, post("/api/v1/subscriptions/purchase", """
                {"user_id":"%s","plan_id":"%s","card_number":"%s","exp_month":12,
                 "exp_year":2099,"cvc":"123"}""".formatted(victima, business, APROBADA)));
    }

    // ------------------------------------------------------------------ apoyo

    private String planActual(String token, String usuario) throws Exception {
        for (JsonNode s : cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/subscriptions/users/" + usuario)))) {
            if ("ACTIVE".equals(s.get("Status").asText())) {
                return s.get("PlanID").asText();
            }
        }
        return null;
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

    private MvcResult ejecutar(String token, int esperado, MockHttpServletRequestBuilder peticion)
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
