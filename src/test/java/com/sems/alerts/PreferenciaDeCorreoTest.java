package com.sems.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sems.alerts.domain.repositories.AlertRepositories.NotificationLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * El correo de alerta respeta la preferencia del usuario.
 *
 * <p>La preferencia se guardaba pero nadie la consultaba: apagar el correo en la
 * pantalla de Alertas no cambiaba nada. Estas pruebas fallan contra ese codigo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreferenciaDeCorreoTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NotificationLogRepository bitacora;

    @Test
    @DisplayName("sin preferencia guardada el aviso sale por correo")
    void porDefectoSeEnvia() throws Exception {
        var cuenta = registrar("correo-por-defecto@energix.test");
        String alerta = alerta(cuenta);

        assertThat(esperarCorreos(alerta)).isEqualTo(1);
    }

    @Test
    @DisplayName("con el correo apagado el aviso no se envia")
    void apagadoNoSeEnvia() throws Exception {
        var cuenta = registrar("correo-apagado@energix.test");
        ejecutar(cuenta[0], 201, post("/api/v1/notification-preferences", """
                {"user_id":"%s","channel":"email","enabled":false,"min_severity":"medium"}"""
                .formatted(cuenta[1])));
        String alerta = alerta(cuenta);

        Thread.sleep(1500);
        assertThat(bitacora.findByAlertId(UUID.fromString(alerta))).isEmpty();
    }

    // ------------------------------------------------------------------ apoyo

    private int esperarCorreos(String alertId) throws Exception {
        UUID id = UUID.fromString(alertId);
        for (int i = 0; i < 50 && bitacora.findByAlertId(id).isEmpty(); i++) {
            Thread.sleep(100);
        }
        return bitacora.findByAlertId(id).size();
    }

    private String alerta(String[] cuenta) throws Exception {
        return cuerpo(ejecutar(cuenta[0], 201, post("/api/v1/alerts", """
                {"user_id":"%s","device_id":"%s","alert_type":"threshold_exceeded",
                 "title":"Consumo por encima del umbral","message":"Prueba","severity":"high"}"""
                .formatted(cuenta[1], UUID.randomUUID())))).get("alert_id").asText();
    }

    private String[] registrar(String correo) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"%s","password":"Energix2026","role":"STAFF"}"""
                        .formatted(correo))).andReturn();
        JsonNode c = cuerpo(res);
        return new String[] {c.get("token").asText(), c.get("userId").asText()};
    }

    private MockHttpServletRequestBuilder post(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
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
