package com.carmarket.auction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service
public class CurrencyExchangeService {

    private final WebClient webClient;

    public CurrencyExchangeService(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://api.exchangerate-api.com/v4/latest/USD").build();
    }

    @Cacheable(value = "exchange-rate", key = "'usd-pln-' + T(java.time.LocalDate).now()")
    public BigDecimal getUsdToPlnRate() {
        try {
            Map<String, Object> response = webClient.get()
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && response.containsKey("rates")) {
                Map<String, Number> rates = (Map<String, Number>) response.get("rates");
                Number pln = rates.get("PLN");
                if (pln != null) {
                    return BigDecimal.valueOf(pln.doubleValue()).setScale(4, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch exchange rate, using fallback", e);
        }
        return new BigDecimal("4.00"); // Fallback
    }
}
