package com.carmarket.auction.parser;

import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Component
public class PlaywrightConfig {

    @Value("${parser.headless:true}")
    private boolean headless;

    @Value("${parser.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${parser.proxy.server:}")
    private String proxyServer;

    public BrowserContext createContext(Playwright playwright) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
            .setHeadless(headless)
            .setArgs(List.of(
                "--disable-blink-features=AutomationControlled",
                "--disable-web-security",
                "--disable-features=IsolateOrigins,site-per-process"
            ));

        if (proxyEnabled && !proxyServer.isBlank()) {
            launchOptions.setProxy(proxyServer);
        }

        Browser browser = playwright.chromium().launch(launchOptions);

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setViewportSize(1920, 1080)
            .setLocale("en-US")
            .setTimezoneId("America/New_York");

        BrowserContext context = browser.newContext(contextOptions);

        // Inject stealth script to hide Playwright
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        return context;
    }
}
