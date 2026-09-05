package com.sems;

import static org.assertj.core.api.Assertions.assertThat;

import com.sems.alerts.application.AlertCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranca el contexto completo de Spring contra una base de datos en memoria.
 *
 * <p>Existe por un fallo real: los repositorios de cuatro modulos estaban
 * declarados como interfaces anidadas dentro de una clase contenedora. Eso
 * <b>compila sin problemas</b>, pero Spring Data solo detecta interfaces de
 * nivel superior, asi que esos repositorios nunca se registraban como beans y la
 * aplicacion moria al arrancar con "required a bean that could not be found".
 *
 * <p>Los tests de dominio no lo detectaron porque ninguno levanta el contexto.
 * Este si: si un repositorio deja de registrarse, o cualquier dependencia queda
 * sin satisfacer, el test falla aqui y no en el despliegue.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("el contexto de la aplicacion arranca con todas las dependencias resueltas")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("los repositorios de Spring Data de los siete modulos estan registrados")
    void allRepositoriesAreRegistered() {
        var repositories = context.getBeanNamesForType(Repository.class);

        // Trece repositorios: si alguno vuelve a quedar anidado dentro de una
        // clase, desaparece de esta cuenta y el test cae.
        assertThat(repositories).hasSizeGreaterThanOrEqualTo(13);

        // Uno por modulo, para que el fallo diga cual falta.
        assertThat(repositories).anyMatch(n -> n.toLowerCase().contains("alertjpa"));
        assertThat(repositories).anyMatch(n -> n.toLowerCase().contains("paymentjpa"));
        assertThat(repositories).anyMatch(n -> n.toLowerCase().contains("planjpa"));
        assertThat(repositories).anyMatch(n -> n.toLowerCase().contains("anomalyjpa"));
        assertThat(repositories).anyMatch(n -> n.toLowerCase().contains("devicejpa"));
        assertThat(repositories).anyMatch(n -> n.toLowerCase().contains("energymeterjpa"));
        assertThat(repositories).anyMatch(n -> n.toLowerCase().contains("userrepository"));
    }

    @Test
    @DisplayName("el servicio que fallaba en produccion se construye correctamente")
    void alertCommandServiceIsWired() {
        assertThat(context.getBean(AlertCommandService.class)).isNotNull();
    }
}
