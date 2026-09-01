package com.townbasket.payments;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public: the payment methods this deployment accepts.
 *
 * <p>Read before the checkout form renders, so the storefront can only ever
 * show methods the server will actually honour. Cash on Delivery is always
 * present; online UPI appears once a live gateway is configured.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment methods available to customers.")
class PaymentMethodsController {

    private final PaymentService paymentService;

    PaymentMethodsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/methods")
    @Operation(summary = "Payment methods a customer may choose at checkout.")
    PaymentMethodsDto methods() {
        return new PaymentMethodsDto(paymentService.enabledMethods());
    }
}
