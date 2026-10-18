package com.carmarket.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import static com.carmarket.gateway.config.ApibaraRateLimiterTest.config;
import static org.assertj.core.api.Assertions.assertThat;

/** The anonymous Apibara limits come from the environment (application.yml), e.g. after buying a bigger package. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
    "APIBARA_ANON_PER_IP_PER_SECOND=2", "APIBARA_ANON_PER_IP_BURST=8", "APIBARA_ANON_GLOBAL_PER_MONTH=1000"})
class ApibaraRateLimitPropertiesTest {

    @Autowired
    @Qualifier("apibaraAnonymousIpRateLimiter")
    private RedisRateLimiter perIp;
    @Autowired
    @Qualifier("apibaraAnonymousGlobalRateLimiter")
    private RedisRateLimiter global;

    @Test
    void environmentVariablesOverrideTheDefaults() {
        assertThat(config(perIp).getReplenishRate()).isEqualTo(2);
        assertThat(config(perIp).getBurstCapacity()).isEqualTo(8);
        assertThat(config(perIp).getRequestedTokens()).isEqualTo(1);

        RedisRateLimiter.Config shared = config(global);
        assertThat(shared.getBurstCapacity() / shared.getRequestedTokens()).isEqualTo(1000);
    }
}
