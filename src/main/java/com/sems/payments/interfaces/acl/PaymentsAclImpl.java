package com.sems.payments.interfaces.acl;

import com.sems.payments.application.PaymentCommandService;
import com.sems.payments.domain.services.SimulatedCardProcessor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Implementacion de la capa anticorrupcion de pagos. */
@Service
@RequiredArgsConstructor
public class PaymentsAclImpl implements PaymentsAcl {

    private final SimulatedCardProcessor processor;
    private final PaymentCommandService commands;

    @Override
    public CardDecision reviewCard(SimulatedCard card) {
        var d = processor.decide(card.number(), card.expMonth(), card.expYear(), card.cvc());
        return new CardDecision(d.approved(), d.brand(), d.last4(), d.declineReason());
    }

    @Override
    public ChargeReceipt recordCharge(UUID userId, UUID subscriptionId, double amount,
                                      String currency, CardDecision decision) {
        var result = commands.recordSimulatedPayment(subscriptionId, userId, amount, currency,
                "simulated_card " + decision.brand() + " **** " + decision.last4(),
                decision.approved());
        return new ChargeReceipt(result.payment().getPaymentId(),
                result.payment().getStatus().wire(), result.payment().getAmount(),
                result.payment().getCurrency(),
                result.invoice() == null ? null : result.invoice().getInvoiceNumber());
    }
}
