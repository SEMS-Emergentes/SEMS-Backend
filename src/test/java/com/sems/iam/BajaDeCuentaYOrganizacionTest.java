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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Dar de baja una organizacion y una cuenta.
 *
 * <p>No habia forma de hacer ninguna de las dos cosas: ni un cliente podia
 * irse, ni se podian retirar datos de prueba sin entrar a la base a mano. Para
 * un sistema que guarda razon social, RUC, correos y consumo de
 * establecimientos reales, eso choca con el derecho de cancelacion que reconoce
 * la Ley 29733.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BajaDeCuentaYOrganizacionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("archivar la organizacion arrastra sus locales y zonas")
    void archivarArrastraLocalesYZonas() throws Exception {
        JsonNode cuenta = registrar("baja-org@energix.test");
        String token = cuenta.get("token").asText();
        String userId = cuenta.get("userId").asText();

        String org = campo(token, 201, "organization_id", json("/api/v1/organizations", """
                {"legal_name":"Cadena Baja SAC","tax_id":"20611111111",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
        String local = campo(token, 201, "site_id", json("/api/v1/organizations/" + org + "/sites", """
                {"site_code":"BAJ-1","name":"Local","contracted_power_kw":80,
                 "tariff_category":"MT2"}"""));
        campo(token, 201, "zone_id", json("/api/v1/sites/" + local + "/zones", """
                {"name":"Camaras","zone_type":"COLD_STORAGE"}"""));

        ejecutar(token, 204, MockMvcRequestBuilders.delete("/api/v1/organizations/" + org));

        // La organizacion sigue existiendo y se puede leer: archivar no es
        // borrar, y quien la archivo conserva su membresia para reactivarla.
        JsonNode despues = cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/organizations/" + org)));
        assertThat(despues.get("status").asText()).isEqualTo("ARCHIVED");

        // Y sus locales y zonas dejan de estar vigentes: si quedaran activos
        // seguirian contando para el cupo del plan y apareciendo en listados,
        // colgando de una organizacion que ya no existe.
        JsonNode locales = cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/organizations/" + org + "/sites")));
        assertThat(locales).isEmpty();
        JsonNode zonas = cuerpo(ejecutar(token, 200,
                MockMvcRequestBuilders.get("/api/v1/sites/" + local + "/zones")));
        assertThat(zonas).isEmpty();

        // Repetirlo no falla: pedir la baja de lo ya dado de baja no es un error.
        ejecutar(token, 204, MockMvcRequestBuilders.delete("/api/v1/organizations/" + org));
    }

    @Test
    @DisplayName("archivar la organizacion es cosa del administrador")
    void soloElAdministradorArchiva() throws Exception {
        JsonNode duenio = registrar("baja-duenio@energix.test");
        JsonNode ajeno = registrar("baja-ajeno@energix.test");
        String token = duenio.get("token").asText();

        String org = campo(token, 201, "organization_id", json("/api/v1/organizations", """
                {"legal_name":"Cadena Ajena SAC","tax_id":"20622222222",
                 "business_type":"RESTAURANT","owner_user_id":"%s"}"""
                .formatted(duenio.get("userId").asText())));

        ejecutar(ajeno.get("token").asText(), 403,
                MockMvcRequestBuilders.delete("/api/v1/organizations/" + org));
    }

    @Test
    @DisplayName("la baja de cuenta borra el correo, cierra la sesion y no deja volver")
    void bajaDeCuenta() throws Exception {
        JsonNode cuenta = registrar("baja-cuenta@energix.test");
        String token = cuenta.get("token").asText();

        ejecutar(token, 200, MockMvcRequestBuilders.get("/api/v1/users/me"));
        ejecutar(token, 204, MockMvcRequestBuilders.delete("/api/v1/users/me"));

        // El token sigue siendo criptograficamente valido durante dos horas, asi
        // que la baja tiene que comprobarse en el filtro: si solo se mirara al
        // entrar, la cuenta cerrada seguiria operando hasta que caducara.
        ejecutar(token, 401, MockMvcRequestBuilders.get("/api/v1/users/me"));

        // Y no se vuelve a entrar.
        MvcResult login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"baja-cuenta@energix.test","password":"Energix2026"}"""))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(401);

        // El correo queda libre: el dato personal se fue, no solo se marco.
        MvcResult realta = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"baja-cuenta@energix.test","password":"Energix2026",
                         "role":"STAFF"}""")).andReturn();
        assertThat(realta.getResponse().getStatus())
                .as("el correo tiene que quedar libre: " + realta.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("no se cierra la cuenta del unico administrador de una organizacion viva")
    void elUnicoAdministradorNoSeVa() throws Exception {
        JsonNode cuenta = registrar("baja-solo@energix.test");
        String token = cuenta.get("token").asText();

        String org = campo(token, 201, "organization_id", json("/api/v1/organizations", """
                {"legal_name":"Cadena Sola SAC","tax_id":"20633333333",
                 "business_type":"WAREHOUSE","owner_user_id":"%s"}"""
                .formatted(cuenta.get("userId").asText())));

        // Dejarle irse convertiria la organizacion en algo que nadie puede
        // gestionar ni dar de baja: viva y fuera de alcance para siempre.
        MvcResult rechazo = ejecutar(token, 409, MockMvcRequestBuilders.delete("/api/v1/users/me"));
        assertThat(rechazo.getResponse().getContentAsString()).contains("only administrator");

        // Archivada la organizacion, la baja procede.
        ejecutar(token, 204, MockMvcRequestBuilders.delete("/api/v1/organizations/" + org));
        ejecutar(token, 204, MockMvcRequestBuilders.delete("/api/v1/users/me"));
    }

    // ------------------------------------------------------------------ apoyo

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

    private MockHttpServletRequestBuilder json(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String campo(String token, int esperado, String nombre,
                         MockHttpServletRequestBuilder peticion) throws Exception {
        return cuerpo(ejecutar(token, esperado, peticion)).get(nombre).asText();
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
