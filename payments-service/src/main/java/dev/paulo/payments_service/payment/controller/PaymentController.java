package dev.paulo.payments_service.payment.controller;

import dev.paulo.payments_service.payment.dto.CreatePaymentRequest;
import dev.paulo.payments_service.payment.dto.PaymentResponse;
import dev.paulo.payments_service.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = payments.pay(customerId(jwt), request.orderId());
        URI location = URI.create("/payments/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    private UUID customerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
