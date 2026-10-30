package dev.paulo.orders_service.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void newOrder_isPendingWithZeroTotal() {
        Order order = new Order(UUID.randomUUID());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void addItem_addsToItemsAndUpdatesTotal() {
        Order order = new Order(UUID.randomUUID());

        order.addItem(UUID.randomUUID(), 2, new BigDecimal("39.99"));
        order.addItem(UUID.randomUUID(), 1, new BigDecimal("39.90"));

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("119.88");
    }
}
