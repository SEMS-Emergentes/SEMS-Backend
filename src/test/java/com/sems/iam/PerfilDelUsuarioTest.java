package com.sems.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * El formulario de alta pedia el nombre completo y el segmento desde el primer
 * dia, pero el backend los descartaba: la web los guardaba en el navegador, de
 * modo que al entrar desde otro equipo la cuenta volvia a llamarse como la
 * parte local del correo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerfilDelUsuarioTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("el alta guarda el nombre y el segmento, y /users/me los devuelve")
    void elAltaGuardaElPerfil() throws Exception {
        JsonNode alta = registrar("""
                {"emailAddress":"perfil@energix.test","password":"Energix2026","role":"STAFF",
                 "fullName":"  Alexis Quispe  ","segment":"retail_chain"}""", 200);

        assertThat(alta.get("fullName").asText()).isEqualTo("Alexis Quispe");
        assertThat(alta.get("segment").asText()).isEqualTo("RETAIL_CHAIN");

        JsonNode yo = cuerpo(mvc.perform(MockMvcRequestBuilders.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + alta.get("token").asText())).andReturn());
        assertThat(yo.get("fullName").asText()).isEqualTo("Alexis Quispe");
        assertThat(yo.get("segment").asText()).isEqualTo("RETAIL_CHAIN");
    }

    @Test
    @DisplayName("el nombre y el segmento son opcionales")
    void sinPerfilTambienSeDaDeAlta() throws Exception {
        JsonNode alta = registrar("""
                {"emailAddress":"perfil-vacio@energix.test","password":"Energix2026",
                 "role":"STAFF"}""", 200);
        assertThat(alta.has("fullName")).isTrue();
        assertThat(alta.get("fullName").isNull()).isTrue();
    }

    @Test
    @DisplayName("un segmento inventado es 400 y dice cuales valen")
    void segmentoInvalido() throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"perfil-malo@energix.test","password":"Energix2026",
                         "role":"STAFF","segment":"FABRICA"}""")).andReturn();
        // Antes IllegalArgumentException salia como 401 y mandaba a revisar la
        // sesion por un campo mal escrito.
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(res.getResponse().getContentAsString()).contains("RETAIL_CHAIN");
    }

    @Test
    @DisplayName("el usuario cambia su propio perfil y el cambio persiste")
    void cambiarElPerfilPropio() throws Exception {
        JsonNode alta = registrar("""
                {"emailAddress":"perfil-cambio@energix.test","password":"Energix2026",
                 "role":"STAFF","fullName":"Nombre Viejo","segment":"INDEPENDENT"}""", 200);
        String token = alta.get("token").asText();

        JsonNode tras = cuerpo(mvc.perform(MockMvcRequestBuilders.put("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Nombre Nuevo","segment":"RETAIL_CHAIN"}""")).andReturn());
        assertThat(tras.get("fullName").asText()).isEqualTo("Nombre Nuevo");
        assertThat(tras.get("segment").asText()).isEqualTo("RETAIL_CHAIN");

        // Y al volver a entrar sigue ahi: es el punto de haberlo guardado.
        JsonNode sesion = cuerpo(mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"perfil-cambio@energix.test","password":"Energix2026"}"""))
                .andReturn());
        assertThat(sesion.get("fullName").asText()).isEqualTo("Nombre Nuevo");
        assertThat(sesion.get("segment").asText()).isEqualTo("RETAIL_CHAIN");
    }

    @Test
    @DisplayName("sin sesion no se cambia ningun perfil")
    void sinSesionNoSeCambiaNada() throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Intruso","segment":"RETAIL_CHAIN"}""")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    private JsonNode registrar(String cuerpo, int esperado) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
        assertThat(res.getResponse().getStatus())
                .as(res.getResponse().getContentAsString()).isEqualTo(esperado);
        return cuerpo(res);
    }

    private JsonNode cuerpo(MvcResult res) throws Exception {
        return JSON.readTree(res.getResponse().getContentAsString());
    }
}
