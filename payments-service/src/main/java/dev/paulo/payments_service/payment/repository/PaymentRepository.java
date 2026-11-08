package dev.paulo.payments_service.payment.repository;

import dev.paulo.payments_service.payment.domain.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {}
