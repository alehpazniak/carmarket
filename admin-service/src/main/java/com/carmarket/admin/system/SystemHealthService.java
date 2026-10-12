package com.carmarket.admin.system;

import com.carmarket.admin.config.AdminProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Asks every instance Eureka knows about for /actuator/health, all in parallel with a short timeout,
 * and reports expected services that have no instance at all as DOWN.
 */
@Slf4j
@Service
public class SystemHealthService {

    private final DiscoveryClient discoveryClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final List<String> expectedServices;
    private final HttpClient httpClient;
    /** Not the common pool: with few CPUs it would run the blocking probes one after another. */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "health-probe");
        t.setDaemon(true);
        return t;
    });

    public SystemHealthService(DiscoveryClient discoveryClient, ObjectMapper objectMapper, AdminProperties properties) {
        this.discoveryClient = discoveryClient;
        this.objectMapper = objectMapper;
        this.timeout = properties.system().healthTimeout() != null ? properties.system().healthTimeout() : Duration.ofSeconds(2);
        this.expectedServices = properties.system().expectedServices() != null
            ? properties.system().expectedServices().stream().map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).toList()
            : List.of();
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public record InstanceHealth(String service, String instanceId, String status, Long latencyMs, String error) {
    }

    public record SystemHealth(Instant checkedAt, String overall, List<InstanceHealth> instances) {
    }

    public SystemHealth check() {
        List<ServiceInstance> instances = new ArrayList<>();
        Set<String> registered = new TreeSet<>();
        for (String serviceId : discoveryClient.getServices()) {
            List<ServiceInstance> found = discoveryClient.getInstances(serviceId);
            if (!found.isEmpty()) registered.add(serviceId.toLowerCase(Locale.ROOT));
            instances.addAll(found);
        }

        List<CompletableFuture<InstanceHealth>> calls = instances.stream()
            .map(i -> CompletableFuture.supplyAsync(() -> probe(i), executor))
            .toList();
        List<InstanceHealth> results = new ArrayList<>(calls.stream().map(CompletableFuture::join).toList());

        for (String expected : expectedServices) {
            if (!registered.contains(expected)) {
                results.add(new InstanceHealth(expected, null, "DOWN", null, "No registered instance"));
            }
        }
        results.sort(Comparator.comparing(InstanceHealth::service).thenComparing(h -> String.valueOf(h.instanceId())));

        boolean allUp = results.stream().allMatch(h -> "UP".equals(h.status()));
        return new SystemHealth(Instant.now(), allUp ? "UP" : "DEGRADED", results);
    }

    private InstanceHealth probe(ServiceInstance instance) {
        String service = instance.getServiceId().toLowerCase(Locale.ROOT);
        String instanceId = instance.getInstanceId() != null ? instance.getInstanceId()
            : instance.getHost() + ":" + instance.getPort();
        URI uri = URI.create(instance.getUri().toString().replaceAll("/$", "") + "/actuator/health");
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString());
            long latency = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new InstanceHealth(service, instanceId, statusOf(response), latency,
                response.statusCode() >= 400 ? "HTTP " + response.statusCode() : null);
        } catch (Exception e) {
            long latency = Duration.ofNanos(System.nanoTime() - start).toMillis();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.debug("Health check of {} failed: {}", uri, e.toString());
            return new InstanceHealth(service, instanceId, "DOWN", latency, e.getClass().getSimpleName());
        }
    }

    /** Spring Boot answers 503 with {"status":"DOWN"} when unhealthy; anything unreadable counts as DOWN. */
    private String statusOf(HttpResponse<String> response) {
        try {
            JsonNode status = objectMapper.readTree(response.body()).path("status");
            if (status.isTextual()) return status.asText();
        } catch (Exception ignored) {
            // fall through
        }
        return response.statusCode() < 400 ? "UNKNOWN" : "DOWN";
    }

    /** For the UI: expected services, so it can show which ones are missing even before any check. */
    public Set<String> expectedServices() {
        return expectedServices.stream().collect(Collectors.toCollection(TreeSet::new));
    }
}
