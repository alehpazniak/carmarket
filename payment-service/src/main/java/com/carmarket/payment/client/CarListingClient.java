package com.carmarket.payment.client;

import com.carmarket.payment.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads a listing from car-service (public GET /cars/{id}) through Eureka.
 * <p>
 * Built from its own {@link RestClient.Builder} with the load-balancer interceptor added, which is
 * what {@code @LoadBalanced} would do. A {@code @LoadBalanced RestClient.Builder} bean would replace
 * Boot's shared builder, and Przelewy24Client would then try to resolve przelewy24.pl via Eureka.
 */
@Slf4j
@Component
public class CarListingClient {

    /** The fields of car-service's CarListingResponse this service needs. */
    public record CarListingSummary(UUID id, UUID sellerId, String status) {
    }

    private final RestClient restClient;

    public CarListingClient(LoadBalancerInterceptor loadBalancerInterceptor,
                            @Value("${payment.car-service-url:http://car-service}") String baseUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestInterceptor(loadBalancerInterceptor)
            .build();
    }

    /** Empty when car-service answers 404 (missing or removed listing). */
    public Optional<CarListingSummary> findListing(UUID carId) {
        try {
            return Optional.ofNullable(restClient.get()
                .uri("/cars/{id}", carId)
                .retrieve()
                .body(CarListingSummary.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("car-service lookup failed for listing {}: {}", carId, e.getMessage());
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Listing service is unavailable");
        }
    }
}
