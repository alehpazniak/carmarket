package com.carmarket.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Price list from application.yml (payment.products.*). Prices are never accepted from the client. */
@Data
@ConfigurationProperties(prefix = "payment")
public class PaymentProductProperties {

    private Map<String, Product> products = new LinkedHashMap<>();

    public Optional<Product> find(String code) {
        return Optional.ofNullable(products.get(code));
    }

    @Data
    public static class Product {
        /** In grosz. */
        private int amount;
        private String currency = "PLN";
        private String description;
    }
}
