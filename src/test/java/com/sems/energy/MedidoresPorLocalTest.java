package com.sems.energy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Los medidores pertenecen a un local y a una zona (US19 a US22).
 *
 * <p>Antes el medidor colgaba solo de un usuario y guardaba su ubicacion como
 * texto libre. Para un supermercado eso no alcanza: un local tiene decenas de
 * medidores repartidos entre camaras de frio, sala de ventas y climatizacion, y
 * atribuir el consumo al area correcta es lo que el cliente compra. Cuatro
 * historias del backlog dependian de un dato que el modelo no guardaba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedidoresPorLocalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private String token;
    private String userId;
    private String local;
    private String otroLocal;
    private String zonaFrio;
    private String zonaVentas;
    private String zonaDeOtroLocal;

    @BeforeAll
    void prepararUnLocalConDosZonas() throws Exception {
        JsonNode cuenta = registrar("medidores@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", enviar("/api/v1/organizations", """
                {"legal_name":"Cadena Medidores SAC","tax_id":"20511111111",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));

        local = campo(201, "site_id", enviar("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"MED-A","name":"Tienda A","contracted_power_kw":120,
                 "tariff_category":"MT2"}"""));
        otroLocal = campo(201, "site_id",
                enviar("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"MED-B","name":"Tienda B","contracted_power_kw":90,
                 "tariff_category":"MT2"}"""));

        zonaFrio = campo(201, "zone_id", enviar("/api/v1/sites/" + local + "/zones", """
                {"name":"Camaras de frio","zone_type":"COLD_STORAGE","operates_off_hours":true}"""));
        zonaVentas = campo(201, "zone_id", enviar("/api/v1/sites/" + local + "/zones", """
                {"name":"Sala de ventas","zone_type":"SALES_FLOOR"}"""));
        zonaDeOtroLocal = campo(201, "zone_id", enviar("/api/v1/sites/" + otroLocal + "/zones", """
                {"name":"Almacen","zone_type":"WAREHOUSE"}"""));
    }

    @Test
    @DisplayName("US19: el alta exige local y acepta zona")
    void elAltaExigeLocal() throws Exception {
        JsonNode medidor = cuerpo(ejecutar(201, enviar("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"%s","meter_serial":"US19-OK"}"""
                .formatted(userId, local, zonaFrio))));
        assertThat(medidor.get("site_id").asText()).isEqualTo(local);
        assertThat(medidor.get("zone_id").asText()).isEqualTo(zonaFrio);

        // Sin local no se registra: la tarifa y el recibo son del suministro.
        ejecutar(400, enviar("/api/v1/energy-meters", """
                {"user_id":"%s","meter_serial":"US19-SIN-LOCAL"}""".formatted(userId)));

        // La zona es opcional: al abrir un local puede que no esten definidas.
        JsonNode sinZona = cuerpo(ejecutar(201, enviar("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"US19-SIN-ZONA"}"""
                .formatted(userId, local))));
        assertThat(sinZona.has("zone_id")).isFalse();
    }

    @Test
    @DisplayName("US19: una zona de otro local no vale")
    void laZonaTieneQueSerDelLocal() throws Exception {
        // Sin esta comprobacion se podria colgar un medidor de la zona de otro
        // establecimiento y el consumo quedaria atribuido al area equivocada,
        // que es justamente el dato por el que el cliente paga.
        ejecutar(400, enviar("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"%s","meter_serial":"US19-CRUZADA"}"""
                .formatted(userId, local, zonaDeOtroLocal)));

        ejecutar(400, enviar("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"no-es-un-uuid",
                 "meter_serial":"US19-MALA"}""".formatted(userId, local)));

        // Un local que no existe es 404; uno archivado seria 409. Antes esta
        // misma situacion respondia 400 aqui, 404 en dispositivos y 409 en
        // organizaciones.
        ejecutar(404, enviar("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"11111111-2222-3333-4444-555555555555",
                 "meter_serial":"US19-FANTASMA"}""".formatted(userId)));
    }

    @Test
    @DisplayName("US20 y US21: los medidores se listan por local y por zona")
    void seListanPorLocalYPorZona() throws Exception {
        String id = campo(201, "id", enviar("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"%s","meter_serial":"US20-A"}"""
                .formatted(userId, local, zonaVentas)));

        assertThat(series("/api/v1/energy-meters/site/" + local)).contains("US20-A");
        assertThat(series("/api/v1/energy-meters/zone/" + zonaVentas)).contains("US20-A");

        // No se cuela en la zona vecina ni en el local vecino.
        assertThat(series("/api/v1/energy-meters/zone/" + zonaFrio)).doesNotContain("US20-A");
        assertThat(series("/api/v1/energy-meters/site/" + otroLocal)).doesNotContain("US20-A");

        // Y un medidor desvinculado deja de aparecer en el listado del local.
        ejecutar(204, MockMvcRequestBuilders.delete("/api/v1/energy-meters/" + id));
        assertThat(series("/api/v1/energy-meters/site/" + local)).doesNotContain("US20-A");
    }

    @Test
    @DisplayName("US22: un medidor se traslada entre zonas del mismo local")
    void seTrasladaEntreZonas() throws Exception {
        String id = campo(201, "id", enviar("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","zone_id":"%s","meter_serial":"US22-A"}"""
                .formatted(userId, local, zonaFrio)));
        String zona = "/api/v1/energy-meters/" + id + "/zone";

        JsonNode movido = cuerpo(ejecutar(200, mover(zona, """
                {"zone_id":"%s"}""".formatted(zonaVentas))));
        assertThat(movido.get("zone_id").asText()).isEqualTo(zonaVentas);
        assertThat(movido.get("site_id").asText()).isEqualTo(local);

        assertThat(series("/api/v1/energy-meters/zone/" + zonaVentas)).contains("US22-A");
        assertThat(series("/api/v1/energy-meters/zone/" + zonaFrio)).doesNotContain("US22-A");

        // A otro local no: seria otro suministro, otra tarifa y otro recibo.
        ejecutar(400, mover(zona, """
                {"zone_id":"%s"}""".formatted(zonaDeOtroLocal)));

        // Dejarlo sin zona es valido: sigue siendo del local.
        JsonNode sinZona = cuerpo(ejecutar(200, mover(zona, "{\"zone_id\":null}")));
        assertThat(sinZona.has("zone_id")).isFalse();
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

    private MockHttpServletRequestBuilder enviar(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder mover(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.patch(ruta).contentType(MediaType.APPLICATION_JSON)
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

    private List<String> series(String ruta) throws Exception {
        JsonNode lista = cuerpo(ejecutar(200, MockMvcRequestBuilders.get(ruta)));
        List<String> series = new ArrayList<>();
        lista.forEach(m -> series.add(m.get("meter_serial").asText()));
        return series;
    }

    private JsonNode cuerpo(MvcResult res) throws Exception {
        return JSON.readTree(res.getResponse().getContentAsString());
    }
}
