package com.sems.payments.interfaces.acl;

import java.util.UUID;

/**
 * Capa anticorrupcion del bounded context de pagos.
 *
 * <p>Existe para que suscripciones pueda cobrar un plan sin conocer pagos,
 * comprobantes ni el procesador: pide una decision sobre la tarjeta y luego
 * registra el cobro contra la suscripcion que corresponda.
 */
public interface PaymentsAcl {

    /** Datos de tarjeta tal como llegan del cliente. No se persisten. */
    record SimulatedCard(String number, Integer expMonth, Integer expYear, String cvc) {

        // Un registro imprime todos sus campos: sin esto, cualquier log o
        // traza que lo toque escribiria el numero completo y el CVC.
        @Override
        public String toString() {
            return "SimulatedCard[****]";
        }
    }

    record CardDecision(boolean approved, String brand, String last4, String declineReason) {
    }

    record ChargeReceipt(UUID paymentId, String status, double amount, String currency,
                         String invoiceNumber) {
    }

    /** Valida la tarjeta y decide el cobro. Lanza un error de validacion si esta mal formada. */
    CardDecision reviewCard(SimulatedCard card);

    /** Deja constancia del cobro, aprobado o rechazado, con su comprobante si se cobro. */
    ChargeReceipt recordCharge(UUID userId, UUID subscriptionId, double amount, String currency,
                               CardDecision decision);
}
