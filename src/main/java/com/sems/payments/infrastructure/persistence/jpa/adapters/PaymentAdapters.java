package com.sems.payments.infrastructure.persistence.jpa.adapters;

import com.sems.payments.domain.model.entities.*;
import com.sems.payments.domain.repositories.PaymentRepositories.*;
import com.sems.payments.infrastructure.persistence.jpa.entities.PaymentJpaEntities.*;
import com.sems.payments.infrastructure.persistence.jpa.repositories.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Implementaciones JPA de los puertos del modulo de pagos. */
public final class PaymentAdapters {

    private PaymentAdapters() {
    }

    @Repository
    @RequiredArgsConstructor
    public static class PaymentAdapter implements PaymentRepository {
        private final PaymentJpa jpa;

        @Override
        public Payment save(Payment p) {
            return toDomain(jpa.save(new PaymentRow(p.getPaymentId(), p.getSubscriptionId(),
                    p.getUserId(), p.getPaymentMethodId(), p.getAmount(), p.getCurrency(),
                    p.getStatus(), p.getPaymentMethod(), p.getStripePaymentIntentId(),
                    p.getPaidAt(), p.getCreatedAt())));
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return jpa.findById(paymentId).map(PaymentAdapter::toDomain);
        }

        @Override
        public List<Payment> findByUserId(UUID userId) {
            return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(PaymentAdapter::toDomain).toList();
        }

        @Override
        public List<Payment> findBySubscriptionId(UUID subscriptionId) {
            return jpa.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream()
                    .map(PaymentAdapter::toDomain).toList();
        }

        @Override
        public Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId) {
            if (stripePaymentIntentId == null) {
                return Optional.empty();
            }
            return jpa.findByStripePaymentIntentId(stripePaymentIntentId)
                    .map(PaymentAdapter::toDomain);
        }

        private static Payment toDomain(PaymentRow r) {
            return new Payment(r.getPaymentId(), r.getSubscriptionId(), r.getUserId(),
                    r.getPaymentMethodId(), r.getAmount(), r.getCurrency(), r.getStatus(),
                    r.getPaymentMethod(), r.getStripePaymentIntentId(), r.getPaidAt(),
                    r.getCreatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class PaymentMethodAdapter implements PaymentMethodRepository {
        private final PaymentMethodJpa jpa;

        @Override
        public PaymentMethod save(PaymentMethod m) {
            return toDomain(jpa.save(new PaymentMethodRow(m.getPaymentMethodId(), m.getUserId(),
                    m.getType(), m.getBrand(), m.getLast4(), m.getExpMonth(), m.getExpYear(),
                    m.getStripePaymentMethodId(), m.isDefaultMethod(), m.getCreatedAt())));
        }

        @Override
        public Optional<PaymentMethod> findById(UUID paymentMethodId) {
            return jpa.findById(paymentMethodId).map(PaymentMethodAdapter::toDomain);
        }

        @Override
        public List<PaymentMethod> findByUserId(UUID userId) {
            return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(PaymentMethodAdapter::toDomain).toList();
        }

        @Override
        public Optional<PaymentMethod> findDefaultByUserId(UUID userId) {
            return jpa.findFirstByUserIdAndDefaultMethodTrue(userId)
                    .map(PaymentMethodAdapter::toDomain);
        }

        @Override
        public void deleteById(UUID paymentMethodId) {
            jpa.deleteById(paymentMethodId);
        }

        private static PaymentMethod toDomain(PaymentMethodRow r) {
            return new PaymentMethod(r.getPaymentMethodId(), r.getUserId(), r.getType(),
                    r.getBrand(), r.getLast4(), r.getExpMonth(), r.getExpYear(),
                    r.getStripePaymentMethodId(), r.isDefaultMethod(), r.getCreatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class InvoiceAdapter implements InvoiceRepository {
        private final InvoiceJpa jpa;

        @Override
        public Invoice save(Invoice i) {
            return toDomain(jpa.save(new InvoiceRow(i.getInvoiceId(), i.getPaymentId(),
                    i.getInvoiceNumber(), i.getIssuedAt(), i.getTotalAmount(), i.getPdfUrl())));
        }

        @Override
        public Optional<Invoice> findById(UUID invoiceId) {
            return jpa.findById(invoiceId).map(InvoiceAdapter::toDomain);
        }

        @Override
        public Optional<Invoice> findByPaymentId(UUID paymentId) {
            return jpa.findByPaymentId(paymentId).map(InvoiceAdapter::toDomain);
        }

        private static Invoice toDomain(InvoiceRow r) {
            return new Invoice(r.getInvoiceId(), r.getPaymentId(), r.getInvoiceNumber(),
                    r.getIssuedAt(), r.getTotalAmount(), r.getPdfUrl());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class WebhookEventAdapter implements WebhookEventRepository {
        private final WebhookEventJpa jpa;

        @Override
        public PaymentWebhookEvent save(PaymentWebhookEvent e) {
            return toDomain(jpa.save(new WebhookEventRow(e.getEventId(), e.getProvider(),
                    e.getProviderEventId(), e.getEventType(), e.getPayload(), e.isProcessed(),
                    e.getReceivedAt(), e.getProcessedAt())));
        }

        @Override
        public Optional<PaymentWebhookEvent> findByProviderEventId(String providerEventId) {
            return jpa.findByProviderEventId(providerEventId).map(WebhookEventAdapter::toDomain);
        }

        private static PaymentWebhookEvent toDomain(WebhookEventRow r) {
            return new PaymentWebhookEvent(r.getEventId(), r.getProvider(), r.getProviderEventId(),
                    r.getEventType(), r.getPayload(), r.isProcessed(), r.getReceivedAt(),
                    r.getProcessedAt());
        }
    }
}
