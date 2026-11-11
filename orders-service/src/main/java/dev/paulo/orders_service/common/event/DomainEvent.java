package dev.paulo.orders_service.common.event;

import java.util.UUID;

public interface DomainEvent {

    UUID aggregateId();

    String type();
}
