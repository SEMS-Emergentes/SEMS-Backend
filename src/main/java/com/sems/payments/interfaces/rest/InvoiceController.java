package com.sems.payments.interfaces.rest;

import com.sems.payments.application.PaymentQueryService;
import com.sems.payments.interfaces.rest.resources.PaymentResources.InvoiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Comprobantes emitidos por cada cobro completado. */
@Tag(name = "Invoices", description = "Payment receipts")
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final PaymentQueryService queries;

    @Operation(summary = "Gets an invoice by its identifier")
    @PreAuthorize("@accesoPagos.factura(#invoiceId)")
    @GetMapping("/{invoiceId}")
    public InvoiceResponse byId(@PathVariable UUID invoiceId) {
        return InvoiceResponse.from(queries.invoiceById(invoiceId));
    }

    @Operation(summary = "Invoice associated with a payment")
    @PreAuthorize("@accesoPagos.pago(#paymentId)")
    @GetMapping("/payment/{paymentId}")
    public InvoiceResponse byPayment(@PathVariable UUID paymentId) {
        return InvoiceResponse.from(queries.invoiceByPayment(paymentId));
    }
}
