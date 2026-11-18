package dev.paulo.payments_service.inbox;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class InboxConfig {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template);

        var backOff = new ExponentialBackOff(1000, 2);
        backOff.setMaxAttempts(2);

        var handler = new DefaultErrorHandler(recoverer, backOff);

        handler.addNotRetryableExceptions(
                NullPointerException.class, IllegalArgumentException.class, JacksonException.class);

        return handler;
    }
}
