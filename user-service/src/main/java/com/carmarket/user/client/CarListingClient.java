package com.carmarket.user.client;

import com.carmarket.user.exception.CarServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads a listing from car-service (public GET /cars/{id}) through Eureka.
 * <p>
 * Same pattern as payment-service's CarListingClient: its own {@link RestClient} with the
 * load-balancer interceptor added, which is what {@code @LoadBalanced} would do, without replacing
 * Boot's shared {@code RestClient.Builder}. The interceptor is created here rather than injected:
 * spring-retry is on the classpath (via spring-kafka), so Spring Cloud registers a
 * RetryLoadBalancerInterceptor bean and no LoadBalancerInterceptor bean.
 */
@Slf4j
@Component
public class CarListingClient {

    /** The fields of car-service's CarListingResponse this service needs. */
    public record CarListingSummary(UUID id, UUID sellerId, String status) {
    }

    private final RestClient restClient;

    public CarListingClient(LoadBalancerClient loadBalancerClient,
                            LoadBalancerRequestFactory loadBalancerRequestFactory,
                            @Value("${user.car-service-url:http://car-service}") String baseUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient, loadBalancerRequestFactory))
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
        } catch (RestClientException | IllegalStateException e) {
            // IllegalStateException: LoadBalancerInterceptor found no car-service instance
            log.error("car-service lookup failed for listing {}: {}", carId, e.getMessage());
            throw new CarServiceUnavailableException("Listing service is unavailable");
        }
    }
}
