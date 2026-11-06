package dev.paulo.payments_service.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.paulo.payments_service.order_view.domain.OrderView;
import dev.paulo.payments_service.order_view.domain.OrderViewStatus;
import dev.paulo.payments_service.order_view.repository.OrderViewRepository;
import dev.paulo.payments_service.payment.domain.Payment;
import dev.paulo.payments_service.payment.domain.PaymentStatus;
import dev.paulo.payments_service.payment.dto.PaymentResponse;
import dev.paulo.payments_service.payment.exception.OrderNotFoundException;
import dev.paulo.payments_service.payment.exception.OrderNotPayableException;
import dev.paulo.payments_service.payment.provider.PaymentProvider;
import dev.paulo.payments_service.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("119.88");

    @Mock
    OrderViewRepository orderViews;

    @Mock
    PaymentRepository payments;

    @Mock
    PaymentProvider provider;

    @InjectMocks
    PaymentService service;

    @Test
    void pay_pendingOrder_chargesAmountFromOrderViewAndSaves() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        givenOrder(orderId, customerId, OrderViewStatus.PENDING);
        when(provider.charge(eq(orderId), any())).thenReturn(PaymentStatus.SUCCEEDED);
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = service.pay(customerId, orderId);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(saved.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("119.88");
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.amount()).isEqualByComparingTo("119.88");
    }

    @Test
    void pay_providerDeclines_savesFailedPayment() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        givenOrder(orderId, customerId, OrderViewStatus.PENDING);
        when(provider.charge(eq(orderId), any())).thenReturn(PaymentStatus.FAILED);
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = service.pay(customerId, orderId);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void pay_orderNotFoundForCustomer_throwsAndDoesNotCharge() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderViews.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(customerId, orderId)).isInstanceOf(OrderNotFoundException.class);
        verify(provider, never()).charge(any(), any());
        verify(payments, never()).save(any());
    }

    @Test
    void pay_cancelledOrder_throwsAndDoesNotCharge() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        givenOrder(orderId, customerId, OrderViewStatus.CANCELLED);

        assertThatThrownBy(() -> service.pay(customerId, orderId)).isInstanceOf(OrderNotPayableException.class);
        verify(provider, never()).charge(any(), any());
        verify(payments, never()).save(any());
    }

    private void givenOrder(UUID orderId, UUID customerId, OrderViewStatus status) {
        when(orderViews.findByOrderIdAndCustomerId(orderId, customerId))
                .thenReturn(Optional.of(new OrderView(orderId, customerId, AMOUNT, status)));
    }
}
