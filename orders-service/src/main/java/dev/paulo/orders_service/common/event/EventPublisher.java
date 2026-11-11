package dev.paulo.orders_service.common.event;

public interface EventPublisher {

    void publish(DomainEvent event);
}
