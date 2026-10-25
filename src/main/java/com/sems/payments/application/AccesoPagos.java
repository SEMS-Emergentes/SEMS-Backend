package com.sems.payments.application;

import com.sems.payments.domain.repositories.PaymentRepositories.InvoiceRepository;
import com.sems.payments.domain.repositories.PaymentRepositories.PaymentMethodRepository;
import com.sems.payments.domain.repositories.PaymentRepositories.PaymentRepository;
import com.sems.shared.security.AccesoUsuario;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprueba que el pago, el metodo de pago o la factura son del usuario.
 *
 * <p>La factura no lleva dueno propio: cuelga de un pago, y es el pago el que
 * dice de quien es. Esto importa mas aqui que en otros modulos, porque una
 * factura ajena expone importes, fechas y razon social.
 */
@Component("accesoPagos")
@RequiredArgsConstructor
public class AccesoPagos {

    private final PaymentRepository payments;
    private final PaymentMethodRepository paymentMethods;
    private final InvoiceRepository invoices;
    private final AccesoUsuario accesoUsuario;

    @Transactional(readOnly = true)
    public boolean pago(UUID paymentId) {
        if (paymentId == null) {
            return true;
        }
        return payments.findById(paymentId)
                .map(p -> accesoUsuario.esMio(p.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean metodoDePago(UUID paymentMethodId) {
        if (paymentMethodId == null) {
            return true;
        }
        return paymentMethods.findById(paymentMethodId)
                .map(m -> accesoUsuario.esMio(m.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean factura(UUID invoiceId) {
        if (invoiceId == null) {
            return true;
        }
        return invoices.findById(invoiceId)
                .map(f -> pago(f.getPaymentId()))
                .orElse(true);
    }
}
