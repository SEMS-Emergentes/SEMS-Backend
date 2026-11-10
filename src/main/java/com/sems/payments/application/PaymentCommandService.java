package com.sems.payments.application;

import com.sems.payments.domain.model.entities.*;
import com.sems.payments.domain.model.valueobjects.PaymentStatus;
import com.sems.payments.domain.repositories.PaymentRepositories.*;
import com.sems.payments.domain.services.PaymentProvider;
import com.sems.payments.domain.services.PaymentStatusMapper;
import com.sems.shared.errors.AppException;
import com.sems.shared.events.DomainEventBus;
import com.sems.shared.events.DomainEvents;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de cobro. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    /** Marca del metodo cuando el cobro viene de una sesion de Stripe Checkout. */
    private static final String METHOD_CHECKOUT = "stripe_checkout";

    private final PaymentRepository payments;
    private final PaymentMethodRepository methods;
    private final InvoiceRepository invoices;
    private final PaymentProvider provider;
    private final PaymentStatusMapper statusMapper;
    private final DomainEventBus bus;

    /** Resultado de procesar un cobro: el pago y, si prospero, su comprobante. */
    public record PaymentResult(Payment payment, Invoice invoice) {
    }

    /**
     * Cobra con una tarjeta ya guardada.
     *
     * <p>El pago se guarda antes de llamar a Stripe: si la llamada externa falla
     * o el proceso muere a mitad, queda constancia del intento en lugar de un
     * cobro fantasma sin registro.
     */
    @Transactional
    public PaymentResult process(UUID subscriptionId, UUID userId, UUID paymentMethodId,
                                 double amount, String currency, String paymentMethodLabel) {
        PaymentMethod method = methods.findById(paymentMethodId)
                .orElseThrow(() -> AppException.notFound("payment method not found"));
        if (!method.getUserId().equals(userId)) {
            throw AppException.unauthorized("unauthorized resource");
        }

        Payment payment = payments.save(Payment.create(subscriptionId, userId, paymentMethodId,
                amount, currency, paymentMethodLabel));

        var result = provider.createPaymentIntent(new PaymentProvider.CreatePaymentIntentRequest(
                amount, currency, method.getStripePaymentMethodId(),
                userId.toString(), subscriptionId == null ? null : subscriptionId.toString(),
                payment.getPaymentId().toString()));

        PaymentStatus status = statusMapper.fromStripe(result.status());
        switch (status) {
            case PROCESSED -> payment.markProcessed(result.id());
            case FAILED -> payment.markFailed(result.id());
            case CANCELLED -> payment.markCancelled(result.id());
            default -> payment.markProcessing(result.id());
        }
        Payment saved = payments.save(payment);

        Invoice invoice = null;
        if (saved.isPaid()) {
            invoice = invoices.save(Invoice.issueFor(saved.getPaymentId(), saved.getAmount(), ""));
            publishProcessed(saved);
        }
        return new PaymentResult(saved, invoice);
    }

    /**
     * Crea una sesion de Stripe Checkout.
     *
     * <p>El usuario paga en la pagina de Stripe y vuelve a la aplicacion. El
     * registro del cobro no se crea aqui: se crea cuando llega el webhook
     * {@code checkout.session.completed}, que es el unico aviso fiable de que el
     * dinero se movio.
     */
    public PaymentProvider.CheckoutSessionResult createCheckoutSession(
            UUID userId, UUID subscriptionId, String planName, double amount, String currency,
            String successUrl, String cancelUrl) {
        if (successUrl == null || successUrl.isBlank() || cancelUrl == null || cancelUrl.isBlank()) {
            throw AppException.validation("success_url and cancel_url are required");
        }
        return provider.createCheckoutSession(new PaymentProvider.CreateCheckoutSessionRequest(
                userId == null ? null : userId.toString(),
                subscriptionId == null ? null : subscriptionId.toString(),
                planName, amount, currency, successUrl, cancelUrl));
    }

    /**
     * Registra el cobro que confirma una sesion de Checkout.
     *
     * <p>Es idempotente a proposito: Stripe reintenta los webhooks, y sin esta
     * comprobacion un mismo pago se registraria varias veces.
     */
    @Transactional
    public Payment recordCheckoutPayment(UUID userId, UUID subscriptionId, double amount,
                                         String currency, String paymentIntentId) {
        var existing = payments.findByStripePaymentIntentId(paymentIntentId);
        if (existing.isPresent()) {
            log.info("Cobro de checkout ya registrado para el intent {}", paymentIntentId);
            return existing.get();
        }

        Payment payment = Payment.create(subscriptionId, userId, null, amount, currency,
                METHOD_CHECKOUT);
        payment.markProcessed(paymentIntentId);
        Payment saved = payments.save(payment);

        invoices.save(Invoice.issueFor(saved.getPaymentId(), saved.getAmount(), ""));
        publishProcessed(saved);
        return saved;
    }

    /**
     * Registra un cobro hecho con el procesador simulado.
     *
     * <p>Tambien se guarda el rechazado: el historial tiene que mostrar el
     * intento fallido, igual que lo mostraria un cobro real.
     */
    @Transactional
    public PaymentResult recordSimulatedPayment(UUID subscriptionId, UUID userId, double amount,
                                                String currency, String paymentMethodLabel,
                                                boolean approved) {
        Payment payment = Payment.create(subscriptionId, userId, null, amount, currency,
                paymentMethodLabel);
        if (approved) {
            payment.markProcessed(null);
        } else {
            payment.markFailed(null);
        }
        Payment saved = payments.save(payment);

        Invoice invoice = null;
        if (saved.isPaid()) {
            invoice = invoices.save(Invoice.issueFor(saved.getPaymentId(), saved.getAmount(), ""));
            publishProcessed(saved);
        }
        return new PaymentResult(saved, invoice);
    }

    /**
     * Publica el cobro completado.
     *
     * <p>Lo escucha el modulo de notificaciones para enviar el comprobante por
     * correo. Antes viajaba por el topic {@code payments.events}.
     */
    private void publishProcessed(Payment payment) {
        bus.publish(new DomainEvents.PaymentProcessed(payment.getUserId(), payment.getPaymentId(),
                BigDecimal.valueOf(payment.getAmount()), payment.getCurrency(),
                payment.getStatus().wire()));
    }
}
