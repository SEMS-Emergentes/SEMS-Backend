package com.sems.coherencia;

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
 * Incoherencias encontradas recorriendo el producto como usuario nuevo.
 *
 * <p>Ninguna hacia fallar nada a la vista: el sistema aceptaba el dato y
 * seguia. Esa es la razon de que esten juntas aqui — lo que tienen en comun no
 * es el modulo sino la forma de fallar, callando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoherenciaYDatosValidosTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FANTASMA = "11111111-2222-3333-4444-555555555555";

    @Autowired
    private MockMvc mvc;

    private String token;
    private String userId;
    private String organizacion;

    @BeforeAll
    void cuentaConOrganizacion() throws Exception {
        JsonNode cuenta = registrar("coherencia@energix.test");
        token = cuenta.get("token").asText();
        userId = cuenta.get("userId").asText();
        organizacion = campo(201, "organization_id", post("/api/v1/organizations", """
                {"legal_name":"Coherencia SAC","tax_id":"20711111111",
                 "business_type":"SUPERMARKET","owner_user_id":"%s"}""".formatted(userId)));
    }

    @Test
    @DisplayName("un local archivado ya no se edita, y arrastra sus zonas")
    void elArchivadoEsDefinitivo() throws Exception {
        String local = nuevoLocal("ARCH-1", 100);
        String zona = campo(201, "zone_id", post("/api/v1/sites/" + local + "/zones", """
                {"name":"Camaras","zone_type":"COLD_STORAGE"}"""));

        ejecutar(204, MockMvcRequestBuilders.delete("/api/v1/sites/" + local));

        // Antes esto respondia 200: se le podia cambiar nombre, potencia y
        // tarifa despues de darlo de baja, de modo que "archivado" era una
        // etiqueta y no un estado.
        ejecutar(409, put("/api/v1/sites/" + local, """
                {"name":"Resucitado","contracted_power_kw":99,"tariff_category":"MT2"}"""));

        // La zona se va con el local, como ya ocurria al archivar la organizacion.
        JsonNode zonas = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/sites/" + local + "/zones")));
        assertThat(zonas).isEmpty();
        assertThat(zona).isNotBlank();
    }

    @Test
    @DisplayName("un local que no existe es 404 y uno archivado es 409, en los tres modulos")
    void mismaCondicionMismoCodigo() throws Exception {
        String local = nuevoLocal("COD-1", 80);
        ejecutar(204, MockMvcRequestBuilders.delete("/api/v1/sites/" + local));

        // energia
        ejecutar(404, post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"COD-F"}"""
                .formatted(userId, FANTASMA)));
        ejecutar(409, post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"COD-A"}"""
                .formatted(userId, local)));

        // dispositivos
        ejecutar(404, post("/api/v1/device-management/devices", """
                {"externalDeviceCode":"COD-DF","userId":"%s","siteId":"%s","deviceName":"D",
                 "deviceType":"HVAC","connectionProtocol":"WIFI"}""".formatted(userId, FANTASMA)));
        ejecutar(409, post("/api/v1/device-management/devices", """
                {"externalDeviceCode":"COD-DA","userId":"%s","siteId":"%s","deviceName":"D",
                 "deviceType":"HVAC","connectionProtocol":"WIFI"}""".formatted(userId, local)));

        // organizaciones
        ejecutar(409, post("/api/v1/sites/" + local + "/zones", """
                {"name":"Tardia","zone_type":"OFFICES"}"""));
    }

    @Test
    @DisplayName("una lectura no mide energia negativa ni viene del futuro")
    void lecturasCreibles() throws Exception {
        String local = nuevoLocal("LEC-1", 70);
        String medidor = campo(201, "id", post("/api/v1/energy-meters", """
                {"user_id":"%s","site_id":"%s","meter_serial":"LEC-M"}"""
                .formatted(userId, local)));

        // Un kWh negativo llega a restar del gasto del mes; una fecha de 2099
        // deja la serie con un pico al final que nunca caduca.
        ejecutar(400, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","power_watts":10,"voltage":220,"current":1,
                 "frequency":60,"energy_kwh":-500}""".formatted(userId, medidor)));
        ejecutar(400, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","power_watts":-10,"voltage":220,"current":1,
                 "frequency":60,"energy_kwh":1}""".formatted(userId, medidor)));
        ejecutar(400, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","power_watts":10,"voltage":220,"current":1,
                 "frequency":60,"energy_kwh":1,"timestamp":"2099-01-01T00:00:00Z"}"""
                .formatted(userId, medidor)));
        ejecutar(201, post("/api/v1/energy-readings", """
                {"user_id":"%s","meter_id":"%s","power_watts":1200,"voltage":220,"current":5,
                 "frequency":60,"energy_kwh":2.5}""".formatted(userId, medidor)));
    }

    @Test
    @DisplayName("la potencia contratada la pone el local, no la regla")
    void elLocalMandaSobreLaPotencia() throws Exception {
        String local = nuevoLocal("DEM-1", 110);

        // Antes se aceptaba: una regla de 999 kW sobre un local de 110 avisaba
        // a 799 kW, es decir nunca.
        ejecutar(400, post("/api/v1/demand-rules", """
                {"site_id":"%s","user_id":"%s","rule_name":"Imposible",
                 "contracted_power_kw":999,"warning_percent":80}""".formatted(local, userId)));

        // Omitirla es lo normal: la regla solo aporta el margen.
        JsonNode regla = cuerpo(ejecutar(201, post("/api/v1/demand-rules", """
                {"site_id":"%s","user_id":"%s","rule_name":"Aviso","warning_percent":80}"""
                .formatted(local, userId))));
        assertThat(regla.get("contracted_power_kw").asDouble()).isEqualTo(110.0);
        assertThat(regla.get("warning_threshold_kw").asDouble()).isEqualTo(88.0);

        // Y al renegociar la potencia, el aviso se mueve con ella sin tocar la regla.
        ejecutar(200, put("/api/v1/sites/" + local, """
                {"name":"Con mas potencia","contracted_power_kw":200,"tariff_category":"MT2"}"""));
        JsonNode avisos = cuerpo(ejecutar(200, post(
                "/api/v1/sites/" + local + "/demand-evaluations", """
                {"demand_kw":100}""")));
        assertThat(avisos)
                .as("100 kW ya no avisa: el umbral subio con el contrato a 160 kW")
                .isEmpty();
    }

    @Test
    @DisplayName("una prediccion necesita un mes real y un periodo que avance")
    void prediccionesCoherentes() throws Exception {
        ejecutar(400, post("/api/v1/analytics/bill-predictions", """
                {"user_id":"%s","prediction_year":2026,"prediction_month":0,
                 "period_start":"2026-01-01T00:00:00Z","period_end":"2026-01-31T00:00:00Z",
                 "estimated_kwh":1,"estimated_amount":1,"currency":"PEN","tariff_used":0.3,
                 "error_margin_percentage":1}""".formatted(userId)));
        ejecutar(400, post("/api/v1/analytics/bill-predictions", """
                {"user_id":"%s","prediction_year":2026,"prediction_month":5,
                 "period_start":"2026-05-31T00:00:00Z","period_end":"2026-05-01T00:00:00Z",
                 "estimated_kwh":1,"estimated_amount":1,"currency":"PEN","tariff_used":0.3,
                 "error_margin_percentage":1}""".formatted(userId)));
    }

    @Test
    @DisplayName("una suscripcion activa por usuario")
    void unaSolaSuscripcion() throws Exception {
        JsonNode planes = cuerpo(ejecutar(200,
                MockMvcRequestBuilders.get("/api/v1/subscription-plans")));
        String starter = null;
        String business = null;
        for (JsonNode plan : planes) {
            if ("Starter".equals(plan.get("Name").asText())) starter = plan.get("PlanID").asText();
            if ("Business".equals(plan.get("Name").asText())) business = plan.get("PlanID").asText();
        }

        JsonNode cuenta = registrar("coherencia-sub@energix.test");
        String suyo = cuenta.get("token").asText();
        String suId = cuenta.get("userId").asText();

        String sub = cuerpo(ejecutarCon(suyo, 201, post("/api/v1/subscriptions", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(suId, starter))))
                .get("SubscriptionID").asText();

        // Con dos planes vivos no hay respuesta a "en que plan esta este
        // cliente": el cupo tomaba el mas amplio y la facturacion podia cobrar
        // los dos.
        ejecutarCon(suyo, 409, post("/api/v1/subscriptions", """
                {"user_id":"%s","plan_id":"%s"}""".formatted(suId, business)));

        // Cambiar de plan sigue siendo la operacion correcta.
        ejecutarCon(suyo, 200, patch("/api/v1/subscriptions/" + sub + "/change-plan", """
                {"new_plan_id":"%s"}""".formatted(business)));
    }

    @Test
    @DisplayName("dos zonas del mismo local no se llaman igual")
    void zonasConNombrePropio() throws Exception {
        String local = nuevoLocal("ZON-1", 60);
        ejecutar(201, post("/api/v1/sites/" + local + "/zones", """
                {"name":"Camaras de frio","zone_type":"COLD_STORAGE"}"""));
        // Dos grupos identicos en el desglose por zona no los distingue nadie.
        ejecutar(409, post("/api/v1/sites/" + local + "/zones", """
                {"name":"  camaras DE frio ","zone_type":"COLD_STORAGE"}"""));
    }

    @Test
    @DisplayName("dar de baja un equipo dos veces responde lo mismo las dos")
    void bajaDeEquipoIdempotente() throws Exception {
        String local = nuevoLocal("IDE-1", 50);
        String equipo = campo(201, "deviceId", post("/api/v1/device-management/devices", """
                {"externalDeviceCode":"IDE-D","userId":"%s","siteId":"%s","deviceName":"D",
                 "deviceType":"HVAC","connectionProtocol":"WIFI"}""".formatted(userId, local)));

        // Desvincular un medidor dos veces ya respondia 204 las dos; esta
        // devolvia 409 la segunda.
        ejecutar(204, MockMvcRequestBuilders.delete("/api/v1/device-management/devices/" + equipo));
        ejecutar(204, MockMvcRequestBuilders.delete("/api/v1/device-management/devices/" + equipo));
    }

    @Test
    @DisplayName("un texto mas largo que su columna es 400, no 500")
    void textoDemasiadoLargo() throws Exception {
        ejecutar(400, post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"LAR-1","name":"%s","contracted_power_kw":50,
                 "tariff_category":"MT2"}""".formatted("A".repeat(5000))));
    }

    @Test
    @DisplayName("el correo se recorta al entrar")
    void correoConEspacios() throws Exception {
        registrar("coherencia-espacios@energix.test");
        // Pegar el correo con un espacio al final devolvia "must be a
        // well-formed email address", que manda a revisar un correo correcto.
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"  coherencia-espacios@energix.test  ",
                         "password":"Energix2026"}""")).andReturn();
        assertThat(res.getResponse().getStatus())
                .as(res.getResponse().getContentAsString()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ apoyo

    private String nuevoLocal(String codigo, int potencia) throws Exception {
        return campo(201, "site_id", post("/api/v1/organizations/" + organizacion + "/sites", """
                {"site_code":"%s","name":"Local %s","contracted_power_kw":%d,
                 "tariff_category":"MT2"}""".formatted(codigo, codigo, potencia)));
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

    private MockHttpServletRequestBuilder put(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.put(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder patch(String ruta, String cuerpo) {
        return MockMvcRequestBuilders.patch(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String campo(int esperado, String nombre, MockHttpServletRequestBuilder peticion)
            throws Exception {
        return cuerpo(ejecutar(esperado, peticion)).get(nombre).asText();
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
