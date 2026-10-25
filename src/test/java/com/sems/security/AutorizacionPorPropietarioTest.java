package com.sems.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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

/**
 * Comprueba que un usuario autenticado no alcanza los recursos de otro.
 *
 * <p>Existe por un fallo real y grave. La configuracion de seguridad terminaba
 * en {@code anyRequest().authenticated()} y ahi se detenia: cualquier cuenta con
 * una sesion valida podia leer y modificar los recursos de cualquier otra con
 * solo cambiar el identificador de la URL. Se comprobo en produccion editando el
 * local de otro usuario.
 *
 * <p>El test va por {@code MockMvc} sobre el contexto completo y con tokens
 * emitidos por el propio endpoint de registro, no con usuarios simulados. Esa es
 * la diferencia que importa: lo que se ejercita es la cadena de filtros y las
 * anotaciones {@code @PreAuthorize} tal y como corren en el despliegue. Un test
 * con un principal falso habria pasado igual antes del arreglo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutorizacionPorPropietarioTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private String tokenDuenio;
    private String tokenAjeno;
    private String idDuenio;
    private String idOrganizacion;
    private String idLocal;
    private String idMedidor;

    @BeforeAll
    void prepararDosCuentasConDatosDelPrimero() throws Exception {
        JsonNode duenio = registrar("duenio@energix.test");
        JsonNode ajeno = registrar("ajeno@energix.test");
        tokenDuenio = duenio.get("token").asText();
        tokenAjeno = ajeno.get("token").asText();
        idDuenio = duenio.get("userId").asText();

        JsonNode organizacion = cuerpo(ejecutar(post("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"legal_name":"Cadena Energix SAC","trade_name":"Energix",
                         "tax_id":"20512345678","business_type":"SUPERMARKET",
                         "owner_user_id":"%s"}""".formatted(idDuenio)), tokenDuenio, 201));
        idOrganizacion = organizacion.get("organization_id").asText();

        JsonNode local = cuerpo(ejecutar(post("/api/v1/organizations/" + idOrganizacion + "/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"site_code":"LIM-001","name":"Tienda Miraflores",
                         "address":"Av. Larco 100","district":"Miraflores",
                         "floor_area_m2":850,"contracted_power_kw":120,
                         "tariff_category":"MT2"}"""), tokenDuenio, 201));
        idLocal = local.get("site_id").asText();

        JsonNode medidor = cuerpo(ejecutar(post("/api/v1/energy-meters")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"user_id":"%s","meter_serial":"MED-0001","brand":"Schneider",
                         "location":"Tablero general"}""".formatted(idDuenio)), tokenDuenio, 201));
        idMedidor = medidor.get("id").asText();
    }

    @Test
    @DisplayName("el dueno sigue alcanzando sus propios recursos")
    void elDuenioPasa() throws Exception {
        ejecutar(get("/api/v1/sites/" + idLocal), tokenDuenio, 200);
        ejecutar(get("/api/v1/organizations/" + idOrganizacion), tokenDuenio, 200);
        ejecutar(get("/api/v1/users/" + idDuenio + "/organizations"), tokenDuenio, 200);
        ejecutar(get("/api/v1/energy-meters/" + idMedidor), tokenDuenio, 200);
    }

    @DisplayName("un tercero autenticado no lee el local de otro")
    @Test
    void noLeeElLocalAjeno() throws Exception {
        ejecutar(get("/api/v1/sites/" + idLocal), tokenAjeno, 403);
        ejecutar(get("/api/v1/sites/" + idLocal + "/zones"), tokenAjeno, 403);
    }

    @Test
    @DisplayName("un tercero autenticado no modifica ni archiva el local de otro")
    void noModificaElLocalAjeno() throws Exception {
        ejecutar(put("/api/v1/sites/" + idLocal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Secuestrada","contracted_power_kw":1,
                         "tariff_category":"MT2"}"""), tokenAjeno, 403);
        ejecutar(delete("/api/v1/sites/" + idLocal), tokenAjeno, 403);
    }

    @Test
    @DisplayName("un tercero autenticado no ve la organizacion ni sus miembros")
    void noVeLaOrganizacionAjena() throws Exception {
        ejecutar(get("/api/v1/organizations/" + idOrganizacion), tokenAjeno, 403);
        ejecutar(get("/api/v1/organizations/" + idOrganizacion + "/members"), tokenAjeno, 403);
        ejecutar(get("/api/v1/organizations/" + idOrganizacion + "/sites"), tokenAjeno, 403);
    }

    @Test
    @DisplayName("un tercero no se da de alta a si mismo en la organizacion de otro")
    void noSeAutoconcedeMembresia() throws Exception {
        ejecutar(post("/api/v1/organizations/" + idOrganizacion + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"ajeno@energix.test","role":"ORG_ADMIN"}"""),
                tokenAjeno, 403);
    }

    @Test
    @DisplayName("un tercero no lista las organizaciones de otro usuario")
    void noListaLoAjenoPorUserId() throws Exception {
        ejecutar(get("/api/v1/users/" + idDuenio + "/organizations"), tokenAjeno, 403);
        ejecutar(get("/api/v1/device-management/users/" + idDuenio + "/devices"), tokenAjeno, 403);
        ejecutar(get("/api/v1/users/" + idDuenio + "/alerts"), tokenAjeno, 403);
        ejecutar(get("/api/v1/energy-meters/user/" + idDuenio), tokenAjeno, 403);
        ejecutar(get("/api/v1/subscriptions/users/" + idDuenio), tokenAjeno, 403);
        ejecutar(get("/api/v1/payments/user/" + idDuenio), tokenAjeno, 403);
    }

    @Test
    @DisplayName("un tercero no lee, desactiva ni desvincula el medidor de otro")
    void noTocaElMedidorAjeno() throws Exception {
        ejecutar(get("/api/v1/energy-meters/" + idMedidor), tokenAjeno, 403);
        ejecutar(patch("/api/v1/energy-meters/" + idMedidor + "/deactivate"), tokenAjeno, 403);
        ejecutar(delete("/api/v1/energy-meters/" + idMedidor), tokenAjeno, 403);
    }

    @Test
    @DisplayName("un tercero no crea recursos a nombre de otro usuario")
    void noCreaANombreDeOtro() throws Exception {
        ejecutar(post("/api/v1/energy-readings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"user_id":"%s","meter_id":"%s","power_watts":100,"energy_kwh":1}"""
                        .formatted(idDuenio, idMedidor)), tokenAjeno, 403);
        ejecutar(post("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"legal_name":"Suplantada SAC","tax_id":"20599999999",
                         "business_type":"OTHER","owner_user_id":"%s"}""".formatted(idDuenio)),
                tokenAjeno, 403);
    }

    @Test
    @DisplayName("sin token no se alcanza nada, como antes")
    void sinTokenSigueSiendo401() throws Exception {
        mvc.perform(get("/api/v1/sites/" + idLocal))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(401, 403));
    }

    // ------------------------------------------------------------------ apoyo

    private JsonNode registrar(String correo) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailAddress":"%s","password":"Energix2026","role":"STAFF"}"""
                                .formatted(correo)))
                .andReturn();
        assertThat(res.getResponse().getStatus())
                .as("registro de " + correo + ": " + res.getResponse().getContentAsString())
                .isEqualTo(200);
        return cuerpo(res);
    }

    private MvcResult ejecutar(MockHttpServletRequestBuilder peticion, String token, int esperado)
            throws Exception {
        MvcResult res = mvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
        assertThat(res.getResponse().getStatus())
                .as("respuesta: " + res.getResponse().getContentAsString())
                .isEqualTo(esperado);
        return res;
    }

    private JsonNode cuerpo(MvcResult res) throws Exception {
        return JSON.readTree(res.getResponse().getContentAsString());
    }
}
