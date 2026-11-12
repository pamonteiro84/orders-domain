package dev.paulo.payments_service.order_view.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orders_view")
public class OrderView {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderViewStatus status;

    protected OrderView() {}

    public OrderView(UUID orderId, UUID customerId, BigDecimal amount, OrderViewStatus status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderViewStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void cancel() {

        if (this.status == OrderViewStatus.PENDING) {
            status = OrderViewStatus.CANCELLED;
        }
    }
}
