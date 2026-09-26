package com.carmarket.payment.przelewy24;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Przelewy24 account settings. All credentials are optional so the service boots before registration. */
@Data
@ConfigurationProperties(prefix = "przelewy24")
public class Przelewy24Properties {

    private static final String SANDBOX_URL = "https://sandbox.przelewy24.pl";
    private static final String PRODUCTION_URL = "https://secure.przelewy24.pl";

    private boolean sandbox = true;
    private Integer merchantId;
    private Integer posId;
    private String crc;
    private String apiKey;
    /** Frontend return page; "/{paymentId}" is appended per transaction. */
    private String urlReturn;
    private String urlStatus;
    private int timeLimit = 15;
    private String language = "pl";
    private boolean verifyNotificationIp;
    private List<String> notificationIps = new ArrayList<>();

    public String baseUrl() {
        return sandbox ? SANDBOX_URL : PRODUCTION_URL;
    }

    /** posId equals merchantId unless P24 assigned a separate shop. */
    public Integer effectivePosId() {
        return posId != null ? posId : merchantId;
    }

    public boolean isConfigured() {
        return merchantId != null
            && crc != null && !crc.isBlank()
            && apiKey != null && !apiKey.isBlank();
    }

    public String paymentPageUrl(String token) {
        return baseUrl() + "/trnRequest/" + token;
    }
}
