package com.sems.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sems.alerts.application.InactivityEvaluationService;
import com.sems.alerts.domain.model.entities.Alert;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Avisos de equipos que dejaron de reportar.
 *
 * <p>Las reglas de inactividad se podian crear y consultar desde el principio,
 * pero no las evaluaba nadie: {@code findAllActive()} no tenia un solo
 * llamador, asi que la regla era un formulario sin consecuencias y el aviso no
 * llegaba nunca. Estas pruebas fallan contra ese codigo.
 *
 * <p>La revision se llama aqui a mano en vez de esperar al planificador: lo que
 * se comprueba es la decision (a quien se avisa y cuantas veces), no el reloj.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InactividadDeEquipoTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InactivityEvaluationService evaluacion;

    private String token;
    private String userId;
    private String local;
    private String medidor;

    @BeforeAll
    void unLocalConTresEquipos() throws Exception {
        JsonNode cuenta = registrar("inactividad@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Inactividad SAC","tax_id":"20933333333",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
        local = campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"INA-1","name":"Tienda Inactividad","contracted_power_kw":100,
                 "tariff_category":"MT2"}"""));
        medidor = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"INA-M"}"""
                .formatted(userId, local)));

    }

    @Test
    @DisplayName("se avisa del equipo que dejo de reportar, y no del que sigue midiendo")
    void avisaDelQueDejoDeReportar() throws Exception {
        String vitrina = equipo("INA-VIT", "Vitrina de lacteos");
        String camara = equipo("INA-FRIO", "Camara de frio");
        lectura(vitrina, hace(180));
        lectura(camara, hace(2));
        regla(vitrina, 30);
        regla(camara, 30);

        List<Alert> levantadas = evaluacion.evaluateAll();

        Alert aviso = paraEquipo(levantadas, vitrina);
        assertThat(aviso).isNotNull();
        assertThat(aviso.getAlertType()).isEqualTo("INACTIVITY");
        // El mensaje nombra el equipo: un aviso que enumera UUID no se lee.
        assertThat(aviso.getMessage()).contains("Vitrina de lacteos").contains("30");
        // La camara midio hace dos minutos: de esa no hay nada que decir.
        assertThat(paraEquipo(levantadas, camara)).isNull();

        // Y llega a la bandeja del usuario por la via de siempre.
        JsonNode bandeja = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/users/" + userId + "/alerts")));
        assertThat(deEquipo(bandeja, vitrina, "INACTIVITY")).hasSize(1);
    }

    @Test
    @DisplayName("una regla creada sin el campo active nace encendida y avisa")
    void sinActiveNaceEncendida() throws Exception {
        // Con active como boolean primitivo, omitirlo valia false: la regla se
        // guardaba apagada, nunca avisaba y no hay ruta para encenderla.
        String horno = equipo("INA-HORNO", "Horno de panaderia");
        lectura(horno, hace(240));
        JsonNode creada = cuerpo(ejecutar(201, post("/api/v1/inactivity-rules", """
                {"user_id":"%s","device_id":"%s","rule_name":"Sin campo active",
                 "max_inactive_minutes":30}""".formatted(userId, horno))));
        assertThat(creada.get("active").asBoolean()).isTrue();

        assertThat(paraEquipo(evaluacion.evaluateAll(), horno)).isNotNull();
    }

    @Test
    @DisplayName("el equipo que nunca reporto no genera aviso")
    void elQueNuncaReportoNoAvisa() throws Exception {
        String reciencomprado = equipo("INA-NUEVO", "Congelador recien instalado");
        regla(reciencomprado, 30);

        // No se puede distinguir el averiado del recien instalado que aun no ha
        // medido, asi que no se inventa un aviso.
        assertThat(paraEquipo(evaluacion.evaluateAll(), reciencomprado)).isNull();
    }

    @Test
    @DisplayName("mientras siga caido no se repite el aviso en cada revision")
    void noRepiteElAvisoEnCadaRevision() throws Exception {
        String congelador = equipo("INA-REPE", "Congelador de carnes");
        lectura(congelador, hace(200));
        regla(congelador, 30);

        assertThat(paraEquipo(evaluacion.evaluateAll(), congelador)).isNotNull();

        // Tres pasadas mas: el equipo sigue caido, pero quien lee la bandeja no
        // necesita enterarse tres veces mas.
        evaluacion.evaluateAll();
        evaluacion.evaluateAll();
        evaluacion.evaluateAll();

        JsonNode bandeja = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/users/" + userId + "/alerts")));
        assertThat(deEquipo(bandeja, congelador, "INACTIVITY")).hasSize(1);
    }

    @Test
    @DisplayName("si el equipo vuelve a reportar, deja de avisarse")
    void siVuelveDejaDeAvisar() throws Exception {
        String otro = equipo("INA-VUELVE", "Camara de la trastienda");
        lectura(otro, hace(200));
        regla(otro, 30);

        assertThat(paraEquipo(evaluacion.evaluateAll(), otro)).isNotNull();

        // Vuelve a dar senales de vida.
        lectura(otro, hace(1));
        assertThat(paraEquipo(evaluacion.evaluateAll(), otro)).isNull();
    }

    // ------------------------------------------------------------------ apoyo

    private static Alert paraEquipo(List<Alert> avisos, String deviceId) {
        return avisos.stream()
                .filter(a -> a.getDeviceId() != null && a.getDeviceId().toString().equals(deviceId))
                .findFirst().orElse(null);
    }

    private static List<JsonNode> deEquipo(JsonNode bandeja, String deviceId, String tipo) {
        List<JsonNode> encontradas = new java.util.ArrayList<>();
        for (JsonNode aviso : bandeja) {
            if (tipo.equals(aviso.get("alert_type").asText())
                    && !aviso.get("device_id").isNull()
                    && deviceId.equals(aviso.get("device_id").asText())) {
                encontradas.add(aviso);
            }
        }
        return encontradas;
    }

    private String equipo(String codigo, String nombre) throws Exception {
        return campo(201, "deviceId", post("/api/v1/device-management/devices", """
                {"externalDeviceCode":"%s","userId":"%s","siteId":"%s","deviceName":"%s",
                 "deviceType":"REFRIGERATION","connectionProtocol":"WIFI"}"""
                .formatted(codigo, userId, local, nombre)));
    }

    private void regla(String deviceId, int minutos) throws Exception {
        ejecutar(201, post("/api/v1/inactivity-rules", """
                {"user_id":"%s","device_id":"%s","rule_name":"Sin reportar",
                 "max_inactive_minutes":%d,"active":true}"""
                .formatted(userId, deviceId, minutos)));
    }

    private void lectura(String deviceId, Instant cuando) throws Exception {
        ejecutar(201, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","device_id":"%s","power_watts":1500,
                 "voltage":220,"current":7,"frequency":60,"energy_kwh":2.5,"timestamp":"%s"}"""
                .formatted(userId, medidor, deviceId, cuando)));
    }

    private static Instant hace(int minutos) {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofMinutes(minutos));
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
