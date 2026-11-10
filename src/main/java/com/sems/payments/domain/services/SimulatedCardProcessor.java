package com.sems.payments.domain.services;

import com.sems.shared.errors.AppException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Procesador de tarjetas simulado.
 *
 * <p>Mientras no haya pasarela real, el cobro se decide aqui. Se valida la
 * tarjeta como lo haria una pasarela (Luhn, vencimiento, CVC) para que el flujo
 * se pruebe con datos con forma real, y dos numeros de prueba conocidos se
 * rechazan para poder ensayar tambien el camino del cobro fallido.
 *
 * <p>No se guarda ni se registra el numero completo ni el CVC: solo salen de
 * aqui la marca y los ultimos cuatro digitos.
 */
@Service
public class SimulatedCardProcessor {

    /** Numeros de prueba que se rechazan, con el motivo que devolveria una pasarela. */
    private static final Map<String, String> RECHAZADAS = Map.of(
            "4000000000000002", "card_declined",
            "4000000000009995", "insufficient_funds");

    // Se compara con el mes en Lima: una tarjeta que vence este mes sigue
    // valiendo hasta que el mes termine para quien paga, no para UTC.
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    public record Decision(boolean approved, String brand, String last4, String declineReason) {
    }

    public Decision decide(String number, Integer expMonth, Integer expYear, String cvc) {
        String digitos = number == null ? "" : number.replaceAll("[\\s-]", "");
        if (!digitos.matches("\\d{13,19}") || !pasaLuhn(digitos)) {
            throw AppException.validation("card_number is not a valid card number");
        }
        if (expMonth == null || expMonth < 1 || expMonth > 12 || expYear == null || expYear < 2000) {
            throw AppException.validation("exp_month and exp_year must be a valid month and a 4-digit year");
        }
        if (YearMonth.of(expYear, expMonth).isBefore(YearMonth.now(LIMA))) {
            throw AppException.validation("the card has expired");
        }
        if (cvc == null || !cvc.matches("\\d{3,4}")) {
            throw AppException.validation("cvc must have 3 or 4 digits");
        }

        String marca = marca(digitos);
        String ultimos = digitos.substring(digitos.length() - 4);
        String motivo = RECHAZADAS.get(digitos);
        return new Decision(motivo == null, marca, ultimos, motivo);
    }

    private static boolean pasaLuhn(String digitos) {
        int suma = 0;
        boolean doblar = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int d = digitos.charAt(i) - '0';
            if (doblar) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            suma += d;
            doblar = !doblar;
        }
        return suma % 10 == 0;
    }

    private static String marca(String digitos) {
        if (digitos.startsWith("4")) {
            return "visa";
        }
        if (digitos.matches("^(5[1-5]|2[2-7]).*")) {
            return "mastercard";
        }
        if (digitos.matches("^3[47].*")) {
            return "amex";
        }
        return "card";
    }
}
