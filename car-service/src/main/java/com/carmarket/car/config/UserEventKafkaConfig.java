package com.carmarket.car.config;

import com.carmarket.car.dto.UserStatusEvent;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * The default consumer deserializes into PaymentEvent (spring.json.value.default.type), so the
 * user.* listeners get their own factory with UserStatusEvent as the target type.
 */
@Configuration
public class UserEventKafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserStatusEvent> userEventListenerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties(null));
        // The yml's spring.json.* / spring.deserializer.* entries configure the PaymentEvent consumer;
        // JsonDeserializer refuses to be configured by both properties and setters.
        config.keySet().removeIf(key -> key.startsWith("spring.json.") || key.startsWith("spring.deserializer."));
        JsonDeserializer<UserStatusEvent> json = new JsonDeserializer<>(UserStatusEvent.class, false);
        json.addTrustedPackages("*");
        var consumerFactory = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(),
            new ErrorHandlingDeserializer<>(json));
        var factory = new ConcurrentKafkaListenerContainerFactory<String, UserStatusEvent>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
