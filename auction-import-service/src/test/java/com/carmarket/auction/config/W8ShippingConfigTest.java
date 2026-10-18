package com.carmarket.auction.config;

import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.PolandZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** application.yml's import.w8 binds as intended (e.g. over-5-3m-surcharge) and equals the Java defaults. */
class W8ShippingConfigTest {

    @Test
    void applicationYmlBindsToTheSameValuesAsTheDefaults() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        StandardEnvironment env = new StandardEnvironment();
        sources.forEach(env.getPropertySources()::addLast);

        W8ShippingConfig bound = new Binder(ConfigurationPropertySources.get(env)).bind("import.w8", W8ShippingConfig.class).get();

        assertThat(bound.getPriceList()).isEqualTo("W8-2025-26");
        assertThat(bound.getOceanFreightUsd().get(W8Port.NEW_YORK).getQuarter()).isEqualByComparingTo("525");
        assertThat(bound.getOceanFreightUsd().get(W8Port.NEW_YORK).getThird()).isNull();
        assertThat(bound.getPorts().get(W8Port.SEATTLE).getTransitDaysMax()).isNull();
        assertThat(bound.getPorts().get(W8Port.NEW_YORK).getLabel()).isEqualTo("New York (Linden, NJ)");
        assertThat(bound.getRotterdam().getForwarderEur().getThird()).isEqualByComparingTo("480");
        assertThat(bound.getPolandDeliveryPln().getZones().get(PolandZone.C)).isEqualByComparingTo("2100");
        assertThat(bound.getPolandDeliveryPln().surcharge(LengthClass.OVER_5_3M)).isEqualByComparingTo("400");
        assertThat(bound.getPolandDeliveryPln().surcharge(LengthClass.OVER_5M)).isEqualByComparingTo("200");

        assertThat(bound).usingRecursiveComparison().withComparatorForType(java.math.BigDecimal::compareTo, java.math.BigDecimal.class)
            .isEqualTo(new W8ShippingConfig());
    }
}
