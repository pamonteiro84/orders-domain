package dev.paulo.payments_service.common.event;

public interface EventPublisher {

    void publish(DomainEvent event);
}
