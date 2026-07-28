package com.carmarket.auction.parser;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ProxyRotationService {

    @Value("${parser.proxy.list:}")
    private List<String> proxyList;

    private final AtomicInteger counter = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        log.info("Loaded {} proxies for rotation", proxyList.size());
    }

    public String getNextProxy() {
        if (proxyList.isEmpty()) return null;
        int idx = counter.getAndIncrement() % proxyList.size();
        return proxyList.get(idx);
    }

    public boolean hasProxies() {
        return !proxyList.isEmpty();
    }
}
