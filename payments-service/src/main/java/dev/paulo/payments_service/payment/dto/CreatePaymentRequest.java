package dev.paulo.payments_service.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePaymentRequest(@NotNull UUID order_id) {}
