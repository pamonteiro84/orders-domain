package dev.paulo.payments_service.order_view.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderViewTest {

    @Test
    void cancel_pendingOrder_becomesCancelled() {
        OrderView order = orderIn(OrderViewStatus.PENDING);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderViewStatus.CANCELLED);
    }

    @Test
    void cancel_paidOrder_staysPaid() {
        OrderView order = orderIn(OrderViewStatus.PAID);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderViewStatus.PAID);
    }

    @Test
    void cancel_alreadyCancelled_staysCancelled() {
        OrderView order = orderIn(OrderViewStatus.CANCELLED);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderViewStatus.CANCELLED);
    }

    private OrderView orderIn(OrderViewStatus status) {
        return new OrderView(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("39.99"), status);
    }
}
