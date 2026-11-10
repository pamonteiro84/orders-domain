package dev.paulo.payments_service.payment.controller;

import dev.paulo.payments_service.payment.dto.CreatePaymentRequest;
import dev.paulo.payments_service.payment.dto.PaymentResponse;
import dev.paulo.payments_service.payment.idempotency.PaymentIdempotency;
import dev.paulo.payments_service.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService payments;
    private final PaymentIdempotency idempotency;

    public PaymentController(PaymentService payments, PaymentIdempotency idempotency) {
        this.payments = payments;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        UUID customerId = customerId(jwt);
        PaymentResponse response = idempotency.execute(
                customerId,
                idempotencyKey,
                request.orderId(),
                () -> payments.pay(customerId, request.orderId(), idempotencyKey));
        URI location = URI.create("/payments/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    private UUID customerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
