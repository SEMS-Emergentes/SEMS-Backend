package com.sems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SEMS - Monolito modular.
 *
 * <p>Un unico desplegable que contiene los bounded contexts del sistema como
 * modulos independientes bajo {@code com.sems.<modulo>}, cada uno con sus capas
 * domain, application, infrastructure e interfaces.
 *
 * <p>{@code @EnableAsync} permite que los consumidores de eventos de dominio
 * corran fuera del hilo de la peticion, igual que hacian los consumidores de
 * Kafka. {@code @EnableRetry} reemplaza el backoff escrito a mano del envio de
 * correos. {@code @EnableScheduling} deja la puerta abierta a tareas
 * periodicas, pero no hay ninguna: no existe un solo metodo {@code @Scheduled}
 * en el proyecto, asi que nada corre por tiempo. Aqui se describia un detector
 * de inactividad que nunca se escribio; las reglas de inactividad se crean y se
 * consultan por la API, pero nadie las evalua, y de ahi que
 * {@code InactivityRuleRepository.findAllActive()} siga sin un solo llamador.
 */
@EnableAsync
@EnableRetry
@EnableScheduling
@SpringBootApplication
public class SemsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemsBackendApplication.class, args);
    }
}
