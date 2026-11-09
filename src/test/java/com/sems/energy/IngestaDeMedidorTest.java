package com.sems.energy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
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
 * Lecturas que manda el propio medidor, sin que nadie las teclee.
 *
 * <p>Hasta ahora el consumo solo podia entrar por {@code POST
 * /api/v1/energy-readings}, protegido con el JWT de la persona dueña del
 * {@code user_id}. Un aparato no puede usar esa puerta sin llevar encima las
 * credenciales de su dueno, asi que en la practica no entraba nada: el panel,
 * el reparto por equipo y las alertas de demanda se sostenian sobre lecturas
 * que nadie producia.
 *
 * <p>Lo que se vigila aqui es que el medidor tenga identidad propia y que esa
 * identidad sea estrecha: sirve para enviar sus lecturas y nada mas, se corta
 * al desvincularlo y se puede rotar. Y que reenviar un lote no duplique el
 * consumo, porque un aparato que recupera la conexion vuelve a mandar lo que
 * tenia guardado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IngestaDeMedidorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private String token;
    private String userId;
    private String local;
    private String medidor;
    private String clave;

    @BeforeAll
    void unLocalConSuMedidorAcreditado() throws Exception {
        JsonNode cuenta = registrar("ingesta@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();

        String organizacion = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Ingesta Retail SAC","tax_id":"20955555555",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
        local = campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"ING-1","name":"Tienda Ingesta","contracted_power_kw":120,
                 "tariff_category":"MT2"}"""));
        medidor = nuevoMedidor("ING-M");
        clave = credencialDe(medidor);
    }

    @Test
    @DisplayName("la credencial se entrega una vez y se reconoce de un vistazo")
    void laCredencialSeEntregaUnaVez() throws Exception {
        JsonNode emitida = cuerpo(ejecutar(201,
                post("/api/v1/energy-meters/" + nuevoMedidor("ING-CRED") + "/credential", "")));

        assertThat(emitida.get("api_key").asText()).startsWith("sems_mk_");
        assertThat(emitida.get("issued_at").asText()).isNotBlank();

        // El medidor no la publica por ninguna otra via: en claro existe solo
        // en esa respuesta.
        JsonNode ficha = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/energy-meters/" + medidor)));
        assertThat(ficha.toString()).doesNotContain("api_key").doesNotContain("sems_mk_");

        // Lo que si publica es cuando se emitio, que es lo que necesita la
        // interfaz para distinguir entre emitir la primera y rotar la que hay.
        assertThat(ficha.get("credential_issued_at").asText()).isNotBlank();
        JsonNode reciente = cuerpo(ejecutar(201, post(
                "/api/v1/energy-meters/" + nuevoMedidor("ING-SINCLAVE") + "/credential", "")));
        assertThat(reciente.get("api_key").asText()).isNotBlank();
    }

    @Test
    @DisplayName("un medidor sin credencial no informa fecha de emision")
    void sinCredencialNoHayFecha() throws Exception {
        JsonNode ficha = cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/energy-meters/" + nuevoMedidor("ING-VIRGEN"))));
        assertThat(ficha.has("credential_issued_at")).isFalse();
    }

    @Test
    @DisplayName("el medidor manda su lote sin JWT y el consumo queda en la cuenta de su dueno")
    void elMedidorEnviaSuLote() throws Exception {
        Instant primera = hace(40);
        Instant segunda = hace(39);

        JsonNode resultado = cuerpo(ingerir(200, clave, """
                {"readings":[
                  {"energy_kwh":4.5,"power_watts":3200,"voltage":220,"current":9,"frequency":60,
                   "timestamp":"%s"},
                  {"energy_kwh":3.25,"power_watts":2800,"voltage":220,"current":8,"frequency":60,
                   "timestamp":"%s"}]}""".formatted(primera, segunda)));

        assertThat(resultado.get("accepted").asInt()).isEqualTo(2);
        assertThat(resultado.get("duplicated").asInt()).isZero();
        assertThat(resultado.get("rejected")).isEmpty();
        assertThat(resultado.get("meter_id").asText()).isEqualTo(medidor);

        // El dueno las ve como lecturas suyas, ya valoradas con la tarifa del
        // local: el aparato no mando ningun user_id, sale de la credencial.
        JsonNode lectura = porMarca(lecturasDelUsuario(), primera);
        assertThat(lectura.get("user_id").asText()).isEqualTo(userId);
        assertThat(lectura.get("meter_id").asText()).isEqualTo(medidor);
        assertThat(lectura.get("energy_kwh").asDouble()).isEqualTo(4.5);
        assertThat(lectura.get("estimated_cost").asDouble()).isGreaterThan(0);
    }

    @Test
    @DisplayName("reenviar el mismo lote no duplica el consumo")
    void reenviarNoDuplica() throws Exception {
        Instant marca = hace(30);
        String lote = """
                {"readings":[{"energy_kwh":7.0,"power_watts":5000,"voltage":220,"current":12,
                 "frequency":60,"timestamp":"%s"}]}""".formatted(marca);

        assertThat(cuerpo(ingerir(200, clave, lote)).get("accepted").asInt()).isEqualTo(1);

        // Lo que hace un medidor al recuperar la conexion: volver a mandar lo
        // que tenia guardado. Sin esto, el consumo del dia se contaria dos
        // veces y el recibo estimado saldria al doble.
        JsonNode segundoIntento = cuerpo(ingerir(200, clave, lote));
        assertThat(segundoIntento.get("accepted").asInt()).isZero();
        assertThat(segundoIntento.get("duplicated").asInt()).isEqualTo(1);

        assertThat(cuantasConMarca(lecturasDelUsuario(), marca)).isEqualTo(1);
    }

    @Test
    @DisplayName("una lectura invalida no tira el resto del lote")
    void unaLecturaMalaNoTiraElLote() throws Exception {
        Instant buena = hace(20);

        JsonNode resultado = cuerpo(ingerir(200, clave, """
                {"readings":[
                  {"energy_kwh":2.0,"power_watts":1500,"voltage":220,"current":7,"frequency":60,
                   "timestamp":"%s"},
                  {"energy_kwh":9.0,"power_watts":4000,"voltage":220,"current":9,"frequency":60,
                   "timestamp":"%s"}]}"""
                .formatted(buena, Instant.now().plus(Duration.ofDays(2)))));

        assertThat(resultado.get("accepted").asInt()).isEqualTo(1);
        assertThat(resultado.get("rejected")).hasSize(1);
        // Dice cual fallo y por que: el aparato no puede arreglarlo, pero quien
        // lea la respuesta si.
        assertThat(resultado.get("rejected").get(0).get("index").asInt()).isEqualTo(1);
        assertThat(resultado.get("rejected").get(0).get("reason").asText())
                .contains("timestamp cannot be in the future");

        assertThat(porMarca(lecturasDelUsuario(), buena)).isNotNull();
    }

    @Test
    @DisplayName("sin credencial, o con una inventada, no entra nada")
    void sinCredencialNoEntraNada() throws Exception {
        String lote = """
                {"readings":[{"energy_kwh":1.0,"timestamp":"%s"}]}""".formatted(hace(10));

        ingerir(401, null, lote);
        ingerir(401, "sems_mk_esta-clave-no-existe", lote);
    }

    @Test
    @DisplayName("rotar la credencial deja sin valor a la anterior")
    void rotarInvalidaLaAnterior() throws Exception {
        String otro = nuevoMedidor("ING-ROT");
        String vieja = credencialDe(otro);
        String nueva = credencialDe(otro);
        assertThat(nueva).isNotEqualTo(vieja);

        String lote = """
                {"readings":[{"energy_kwh":1.5,"timestamp":"%s"}]}""".formatted(hace(9));
        ingerir(401, vieja, lote);
        assertThat(cuerpo(ingerir(200, nueva, lote)).get("accepted").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("desvincular el medidor le corta la ingesta")
    void desvincularCortaLaIngesta() throws Exception {
        String otro = nuevoMedidor("ING-BAJA");
        String suClave = credencialDe(otro);
        ejecutar(204, MockMvcRequestBuilders.delete("/api/v1/energy-meters/" + otro));

        // Si la credencial sobreviviera, el aparato seguiria escribiendo en una
        // cuenta de la que acaban de sacarlo y que ya nadie mira.
        ingerir(401, suClave, """
                {"readings":[{"energy_kwh":2.5,"timestamp":"%s"}]}""".formatted(hace(8)));
    }

    // ------------------------------------------------------------------ apoyo

    /** Una peticion de ingesta: sin JWT, con la clave del medidor si la hay. */
    private MvcResult ingerir(int esperado, String meterKey, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders
                .post("/api/v1/ingest/readings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
        if (meterKey != null) {
            peticion = peticion.header("X-Meter-Key", meterKey);
        }
        MvcResult res = mvc.perform(peticion).andReturn();
        assertThat(res.getResponse().getStatus())
                .as("respuesta: " + res.getResponse().getContentAsString()).isEqualTo(esperado);
        return res;
    }

    private String nuevoMedidor(String serie) throws Exception {
        return campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"%s"}"""
                .formatted(userId, local, serie)));
    }

    private String credencialDe(String meterId) throws Exception {
        return campo(201, "api_key",
                post("/api/v1/energy-meters/" + meterId + "/credential", ""));
    }

    private JsonNode lecturasDelUsuario() throws Exception {
        return cuerpo(ejecutar(200, MockMvcRequestBuilders
                .get("/api/v1/energy-readings/user/" + userId).param("limit", "200")));
    }

    private static JsonNode porMarca(JsonNode lecturas, Instant marca) {
        for (JsonNode lectura : lecturas) {
            if (Instant.parse(lectura.get("timestamp").asText()).equals(marca)) {
                return lectura;
            }
        }
        throw new AssertionError("no hay lectura con marca " + marca + ": " + lecturas);
    }

    private static int cuantasConMarca(JsonNode lecturas, Instant marca) {
        int total = 0;
        for (JsonNode lectura : lecturas) {
            if (Instant.parse(lectura.get("timestamp").asText()).equals(marca)) {
                total++;
            }
        }
        return total;
    }

    /** Una marca de tiempo pasada y propia de cada prueba, al segundo exacto. */
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
