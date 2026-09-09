package com.sems.energy.domain.model.valueobjects;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Reglas del horario de punta del sistema electrico peruano.
 *
 * <p>Segun el pliego tarifario (OSINERGMIN, Anexo B): <i>"Se entendera por horas de
 * punta (HP) el periodo comprendido entre las 18:00 horas y 23:00 horas de cada dia de
 * todos los meses del ano, exceptuandose a solicitud del cliente, los dias domingos,
 * dias de descanso que correspondan a feriados y feriados que coincidan con dias de
 * descanso"</i>.
 *
 * <p>Conviene leer despacio la parte de "a solicitud del cliente": la hora punta rige
 * <b>todos los dias</b>, domingos incluidos. La exclusion de domingos y feriados no es
 * automatica, es una opcion que el cliente pide a la distribuidora. Por eso no se puede
 * codificar como si fuera universal: depende del contrato de cada suministro y por eso
 * llega como parametro.
 *
 * <p>Para un supermercado la diferencia no es menor. El domingo es uno de sus dias de
 * mayor afluencia, y darlo por fuera de punta cuando no lo esta subestima la factura
 * justo en la franja mas cara.
 */
public final class HorarioPunta {

    public static final int HORA_INICIO = 18;
    public static final int HORA_FIN = 23;

    /** Peru esta en UTC-5 todo el ano: no hay horario de verano. */
    private static final ZoneOffset PERU = ZoneOffset.ofHours(-5);

    private HorarioPunta() {
    }

    /**
     * Franja a la que pertenece un instante, para un suministro sin la exclusion.
     */
    public static FranjaHoraria franjaDe(Instant instante) {
        return franjaDe(instante, false);
    }

    /**
     * Franja a la que pertenece un instante.
     *
     * <p>Se evalua en hora local de Peru, no en UTC. Las lecturas se guardan en UTC, y
     * Peru esta en UTC-5: sin convertir, un consumo de las 19:00 locales se registraria
     * como las 00:00 del dia siguiente y se facturaria como fuera de punta, que es
     * justo lo contrario de lo que es.
     *
     * <p>Los feriados no se contemplan aqui: requieren el calendario oficial del ano,
     * que esta fuera del alcance de este modelo. Un suministro con la exclusion
     * concedida vera bien sus domingos y mal sus feriados; queda anotado como
     * limitacion conocida en vez de resolverse a medias.
     *
     * @param instante         momento de la lectura, en UTC
     * @param excluyeDomingos  si el suministro tiene concedida la exclusion de
     *                         domingos y feriados
     */
    public static FranjaHoraria franjaDe(Instant instante, boolean excluyeDomingos) {
        LocalDateTime local = aHoraLocal(instante);

        if (excluyeDomingos && local.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return FranjaHoraria.FUERA_DE_PUNTA;
        }

        int hora = local.getHour();
        return hora >= HORA_INICIO && hora < HORA_FIN
                ? FranjaHoraria.PUNTA
                : FranjaHoraria.FUERA_DE_PUNTA;
    }

    public static boolean esHoraPunta(Instant instante) {
        return esHoraPunta(instante, false);
    }

    public static boolean esHoraPunta(Instant instante, boolean excluyeDomingos) {
        return franjaDe(instante, excluyeDomingos) == FranjaHoraria.PUNTA;
    }

    public static LocalDateTime aHoraLocal(Instant instante) {
        return LocalDateTime.ofInstant(instante, PERU);
    }
}
